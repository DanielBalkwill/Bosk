# Bosk — Engineering Case Study

> A solo-built, local-first AI learning app for Android — and a study in **defensive design**:
> treating privacy and security as day-one architecture, not a later patch.

**Repository:** https://github.com/DanielBalkwill/Bosk · **Licence:** AGPL-3.0 ·
**Docs:** [README](../README.md) · [Architecture](ARCHITECTURE.md) · [Security policy](../SECURITY.md) · [AI guide](../AGENTS.md)

---

## TL;DR

Bosk fuses an Obsidian-style knowledge graph, Anki-style spaced repetition, a live-preview
Markdown editor, and a notes-grounded AI tutor into one native Android app — built so that
**your data never leaves your device except to the AI provider you explicitly choose.**

| | |
|---|---|
| Platform | Native Android — Kotlin 2.0, Jetpack Compose (Material 3), single-activity |
| Scale | ~24k lines of Kotlin, 154 source files |
| Tests | 140+ JVM unit tests over a pure logic core, run in CI on every push |
| Persistence | Room (SQLite + FTS4), DataStore — fully offline |
| AI | Provider-agnostic BYOK layer (Gemini / OpenAI / Claude / OpenAI-compatible / on-device Nano), streaming |
| Security | API keys in `EncryptedSharedPreferences` (AES-256, Android Keystore); redaction before every AI call; no backend |
| Built by | One second-year cyber security student, over a few months |

---

## 1. Why I built it

I wanted a single app that does what I actually do when I study — write interconnected
notes, turn them into flashcards, and get quizzed — without the usual trade-off of handing
all of that to someone else's cloud. Most "AI note apps" bolt a chatbot onto a product that
already ships your data to a server. I wanted the inverse: a **local-first** app where the AI
is the connective tissue between your notes, your graph, and your revision schedule, and
where *you* decide what, if anything, ever leaves the device.

That constraint — privacy as a hard requirement — is what makes Bosk interesting to build.
It forced real architectural decisions instead of convenient ones.

---

## 2. What it does

**The four pillars** (the AI ties them together):
- **Knowledge graph** — `[[wiki-links]]` in your notes become a live, force-directed graph.
  *The graph is the curriculum*: a curriculum engine reads the graph plus your
  spaced-repetition state to decide what to resurface next.
- **Spaced repetition** — a from-scratch **FSRS-4.5** scheduler; cloze cards generated from
  redaction markers.
- **Markdown editing** — live-preview (rendered in place, the caret line stays raw), full-text
  search, per-note version history with one-tap restore.
- **Ambient companion** — a background daemon + lock-screen overlay that surfaces micro-lessons
  and quizzes on a schedule, with quiet hours.

**AI tutor** — a Socratic study chat grounded in *your* attached notes and daily roadmap, with
a real graded quiz mode: due flashcards are pulled first, and answering them updates their
**actual FSRS schedule**; missed questions become new cards.

**Multi-provider failover (BYOK)** — you can stack keys from several providers in an **ordered
priority chain**. If a request fails or hits a provider's rate limit, Bosk automatically falls
through to the next provider down, so you can glide past any single free-tier limit. The chain
is reorderable in Settings (primary + ordered backups).

**Bring your existing toolkit** — Bosk imports from the tools you already use:
**Obsidian vaults** (folder import, including images), **Anki decks** (`.apkg` + plain text),
and **Quizlet sets** (paste from Quizlet's Export — see the design note in §4).

---

## 3. Architecture

Three feature-grouped layers with one strict rule — **the logic core never depends on the UI.**

```
core/     pure Kotlin logic + data (no Compose) — unit-testable off-device
  ai/        LlmRouter + providers, note-AI services, persona, knowledge profile, roadmap
  data/      Room database (v7), DAOs, 9 entities
  notes/     repositories, Markdown model, wiki-links, redaction, edit histories
  review/ + srs/   FSRS scheduler, cloze cards, Anki/Quizlet import, streaks
  graph/ · commands/ · curriculum/ · settings/ · auth/ · queue/ · cache/ · tts/
ui/       Compose + ViewModels (MVVM); screens observe StateFlow
service/  background: ambient daemon (WorkManager), overlay, notifications, alarms, widget
```

- **Dependency injection:** Hilt, compile-time validated, rooted in `BoskApplication`.
- **Concurrency:** everything is `suspend`/`Flow`; **no AI, network, or DB call touches the
  main thread.** LLM output is a `Flow<String>` so the UI and text-to-speech can consume tokens
  as they stream.
- **Data model:** Room is the single source of truth — notes, the graph (`NodeEntity` /
  `EdgeEntity`), SRS state, and version-history snapshots, with FTS4 for search.

A fuller map lives in [`AGENTS.md`](../AGENTS.md); the rationale behind each decision is in
[`docs/ARCHITECTURE.md`](ARCHITECTURE.md).

---

## 4. Security & privacy — the defensive-design core

This is the part I care about most, and the reason this project doubles as a security
portfolio piece. Bosk has a deliberate, documented threat model (see [`SECURITY.md`](../SECURITY.md)):

- **Keys never leave the device except to their own provider.** API keys live only in
  `EncryptedSharedPreferences` (`core/auth/SecureKeyStore.kt`) — **AES-256-GCM** for values,
  **AES-256-SIV** for key names, master key held in the **Android Keystore**. They are
  **excluded from cloud backup and device-to-device transfer** via explicit backup rules, and
  are never logged.
- **No app backend.** The free tier proxies nothing. There is no server that can see your notes
  or your keys — which also means there is nothing to breach server-side.
- **Data minimisation by redaction.** `⟦redaction⟧` markers (`core/notes/Redaction.kt`) strip
  marked content out of *every* outbound AI request, so you control exactly what reaches a
  third-party model.
- **Least privilege + hardened release builds** (R8/ProGuard minification, only the Android
  permissions the features actually need).

**A design decision worth calling out — the Quizlet import.** The obvious feature was to
"pull any public Quizlet set." I deliberately didn't build that: Quizlet has no public API and
its Terms of Service prohibit automated scraping, and a scraper would also be brittle and
unreliable. Instead, Bosk imports a set from the text Quizlet's own **Export** feature gives
you — reliable, resilient, and ToS-respecting. Choosing the defensible path over the flashy one
*is* the security mindset.

---

## 5. Engineering deep-dives

The problems that were genuinely interesting to solve:

**Provider failover as pure, testable logic.** The ordered-chain router (`LlmRouter`) tries
providers in priority order and falls through on failure. Rather than bury that in a class that
needs Android/encryption to instantiate, I extracted the decision logic into pure functions
(`orderChain`, `generateOverChain`) so the real failover behaviour is covered by fast unit
tests with no Android dependency at all. Streaming is handled carefully: it can fail over
*before* the first token arrives, but once a reply is streaming it won't switch mid-sentence.

**Killing streaming jank (O(n²) → O(n)).** The tutor used to re-parse an entire growing reply's
Markdown on *every* streamed token — quadratic work that stuttered on long answers. Now it
renders raw while streaming and formats once, when the stream settles. Auto-scroll follows the
stream but yields if you've scrolled up to read back.

**Compose recomposition discipline.** The tab-pager reads its scroll offset inside the
`graphicsLayer{}` *draw* phase instead of composition — reading it in composition was
recomposing every visible page's subtree on every scroll frame. That one change was the real
fix for tab-switch lag.

**Graph physics that costs nothing at rest.** The force-directed graph uses a spatial grid and
anneals/cools so it **freezes once settled** — an idle graph renders no frames.

**FSRS as a pure function.** `fsrsSchedule(card, rating, now) → card` has zero Room/UI coupling,
which is exactly why the retention logic can be unit-tested directly.

---

## 6. Testing & CI

The pure `core/` is where the real logic lives, and it's covered by **140+ JVM unit tests**
(FSRS scheduling, Markdown, wiki-link parsing, graph physics helpers, provider failover,
Quizlet parsing, command handling, redaction…). **GitHub Actions** compiles the app and runs
the full suite on every push — a green badge on the README is the public proof that `main`
always builds and passes.

---

## 7. Tech stack

Kotlin 2.0 · Jetpack Compose (Material 3) · Room (SQLite + FTS4) · Hilt · Coroutines/Flow ·
WorkManager · DataStore · OkHttp + kotlinx.serialization · Glance (home-screen widget) ·
Android TextToSpeech. Min SDK 26 / Target SDK 35 · JDK 17.

---

## 8. What building it solo taught me

- **Architecture beats features.** Keeping the logic core free of any UI dependency is the only
  reason I can test it properly — and testability is what let me refactor confidently.
- **"Smooth" is engineering, not luck.** Chasing a locked frame rate taught me how Compose
  actually recomposes, and where the real costs hide (the draw phase, not the obvious places).
- **Security is a day-one decision.** Every "where does this data go?" answer was easy because
  the architecture made the private path the default path. Retrofitting that later is how leaks
  happen.

---

## 9. Status & roadmap

Pre-1.0 and under active development (`versionName 0.1.0`); the schema may still change. The
build and unit suite are CI-verified; device/UX testing is ongoing. On-device Gemini Nano needs
supported hardware (Pixel 8+/S24 class). Optional cloud sync is the only planned paid feature,
and it will never include your keys. Current status and the full feature history live in
[`docs/CHECKPOINTS.md`](CHECKPOINTS.md).

---

*Built by Daniel Balkwill. Feedback and contributions welcome — and I'm open to internships /
placements in security and software engineering. See [`CONTRIBUTING.md`](../CONTRIBUTING.md).*
