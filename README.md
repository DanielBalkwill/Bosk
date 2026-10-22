# Bosk

> *Learn everything. Forget nothing.*

[![Android CI](https://github.com/DanielBalkwill/Bosk/actions/workflows/android.yml/badge.svg)](https://github.com/DanielBalkwill/Bosk/actions/workflows/android.yml)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)
![Platform: Android](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)

**Bosk turns everything you know into a living grove of knowledge** — a knowledge graph
you grow, prune, and regrow through spaced repetition, with an ambient AI that quietly
resurfaces the right idea at the moment you're about to forget it.

It's an **AI-powered learning companion** for Android: Obsidian-style local-first
knowledge management, fused with spaced repetition and an always-on companion. Not a note
app with AI bolted on — a *learning OS* where the AI is the connective tissue between four
pillars.

| Pillar | Inspired by | Bosk's role |
|---|---|---|
| **Knowledge graph** | Obsidian | `[[wiki-links]]` become a living force-directed graph — **the graph is the curriculum** |
| **Spaced repetition** | Anki | FSRS-4.5 scheduling, cloze cards from redactions, Anki `.apkg` import |
| **Markdown editing** | Obsidian | Live-preview editor (rendered in place, caret line raw), reading mode, version history |
| **Ambient companion** | Limitless | Background daemon + lock-screen overlay that speaks micro-lessons and quizzes you |

> **The model is Obsidian's:** free and fully functional offline. The only planned paid
> feature (later) is optional cloud sync — and even then, your keys are never synced.

## Screenshots

> _A short demo and screenshots are coming soon._ To add them: drop images into
> `docs/screenshots/` and uncomment the block below.

<!--
<p align="center">
  <img src="docs/screenshots/home.png"   width="30%" alt="Home / Today" />
  <img src="docs/screenshots/graph.png"  width="30%" alt="Knowledge graph" />
  <img src="docs/screenshots/review.png" width="30%" alt="Spaced-repetition review" />
</p>
-->

## Highlights

- **Bring your own key, or pay nothing** — paste a key from Groq or Cerebras (genuinely
  free, no card), or OpenAI / Anthropic / Gemini / NVIDIA / OpenRouter / any
  OpenAI-compatible endpoint. Provider auto-detected from the key prefix. On-device
  Gemini Nano where hardware allows.
- **Privacy by design** — everything lives on-device; keys in EncryptedSharedPreferences;
  ⟦redaction⟧ markers strip content out of every AI call; notes go only to the provider
  *you* chose. (See [Privacy & security](#privacy--security).)
- **AI everywhere it helps** — editor assistant (edits stream into the note live), bulk
  multi-note edits, a terminal with ~30 real slash commands, a Socratic tutor grounded in
  your notes, an AI graph maker, daily roadmap, ambient micro-lessons.
- **Undo everything** — shared per-note history across editor, terminal and bulk edits,
  plus persisted version snapshots with one-tap restore.
- **Fast** — FTS4 full-text search, quick switcher, spatial-grid graph physics that
  freezes at rest, warm-start caches, baseline-profile-ready.

## Privacy & security

Bosk is **local-first and private by design** — a deliberate threat model, not a slogan:

- **Your notes stay on your device.** Everything lives in a local Room/SQLite database and
  works fully offline. There is no Bosk server and nothing is proxied through one.
- **Bring your own key (BYOK).** You supply your own provider key, so Bosk bears zero AI
  cost and never sees your data. Keys are stored only in `EncryptedSharedPreferences`
  (AES-256, master key in the Android Keystore), excluded from cloud backup and
  device-to-device transfer, and never logged.
- **Data minimisation via redaction.** `⟦redaction⟧` markers strip marked content out of
  every outbound AI request — you control exactly what reaches a third-party model.
- **Hardened release builds** with R8/ProGuard minification and resource shrinking.

Found a vulnerability? Please report it privately — see [`SECURITY.md`](SECURITY.md).

## Stack

- **Kotlin + Jetpack Compose** (Material 3), single-activity, native Android
- **Room** (SQLite + FTS4) · **DataStore** · **Hilt** · **Coroutines/Flow** · **WorkManager**
- **OkHttp + kotlinx.serialization** for the multi-provider LLM layer (streaming SSE)
- **Glance** home-screen widget · **SplashScreen** API · Android TextToSpeech
- ~120 JVM unit tests over the pure core (FSRS, markdown, commands, physics helpers),
  run on every push by [GitHub Actions](https://github.com/DanielBalkwill/Bosk/actions)

Min SDK 26 · Target/Compile SDK 35 · JDK 17 · AGP 8.7 · Kotlin 2.0

## Build & run

> Open directly in **Android Studio** (Ladybug+), or with a command-line SDK:

```bash
cp local.properties.example local.properties   # set sdk.dir
./gradlew testDebugUnitTest                     # run the unit-test suite
./gradlew assembleDebug                         # build the debug APK
./gradlew installDebug                          # install on a device/emulator
```

No API keys in config — AI keys are entered **in-app** (BYOK, encrypted on-device).
Optional Google sign-in needs `GOOGLE_SERVER_CLIENT_ID` in `local.properties`
(see [`.env.example`](.env.example)).

## Project layout

```
app/src/main/java/app/bosk/
  core/
    ai/        LlmRouter + providers (Gemini/OpenAI/Anthropic/Nano/OpenAI-compatible),
               NoteAiService, BulkEditService, persona, prompts, roadmap
    commands/  Slash-command registry + the user-editable command note
    data/      Room database (v7), DAOs, entities (notes, versions, graph, SRS, queues)
    notes/     Repositories, markdown model + edits, wiki-links, redaction, histories
    review/    FSRS-4.5, cloze cards, Anki import, knowledge health, streaks
    srs/       Pure FSRS scheduler
    settings/  DataStore-backed preferences
  ui/
    screens/   home · notes (editor/compiler) · graph (vault/flashcards/AI maker) ·
               review · stats · settings · tutor · terminal · onboarding
    components/  Quick switcher, command palette, shared chrome
  service/     ambient daemon · lock-screen overlay · queue alarms · widget
docs/ARCHITECTURE.md   Why every major decision was made
docs/CHECKPOINTS.md    Checkpoint manifests (the build's resume mechanism)
```

## Status & roadmap

**Pre-1.0, under active development** (`versionName 0.1.0`). The core is functional; some
APIs and the database schema may still change.

- [x] Local-first notes, knowledge graph, FSRS spaced repetition
- [x] Multi-provider BYOK AI layer (streaming) + on-device Nano
- [x] Ambient companion — background daemon & lock-screen overlay
- [ ] Polish pass, screenshots & a short demo video
- [ ] Optional cloud sync (the only planned paid feature; keys never synced)
- [ ] Play Store release

## Contributing

Issues and pull requests are welcome — please read [`CONTRIBUTING.md`](CONTRIBUTING.md)
first (dev setup, conventions, PR process). Security issues go through
[`SECURITY.md`](SECURITY.md), not public issues.

## License

Licensed under the **GNU Affero General Public License v3.0** — see [`LICENSE`](LICENSE).
In short: Bosk is free and open source, and any distributed or network-hosted derivative
must also be open-sourced under the AGPL. As the sole copyright holder, the author may
also offer Bosk under separate commercial terms.
