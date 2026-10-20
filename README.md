# Mnemos

> *Learn everything. Forget nothing.*

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)

An **AI-powered learning companion** for Android — Obsidian-style local-first knowledge
management, fused with spaced repetition and an ambient AI that resurfaces what you're
about to forget. Not a note app with AI bolted on: a *learning OS* where the AI is the
connective tissue between four pillars.

| Pillar | Inspired by | Mnemos role |
|---|---|---|
| Knowledge graph | Obsidian | `[[wiki-links]]` become a living force-directed graph; **the graph is the curriculum** |
| Spaced repetition | Anki | FSRS-4.5 scheduling, cloze cards from redactions, Anki `.apkg` import |
| Markdown editing | Obsidian | Live-preview editor (rendered in place, caret line raw), reading mode, version history |
| Ambient companion | Limitless | Background daemon + lock-screen overlay that speaks micro-lessons and quizzes you |

**The model is Obsidian's:** the app is free and fully functional offline; the only paid
feature (later) is optional cloud sync.

## Highlights

- **Bring your own key, or pay nothing** — paste a key from Groq or Cerebras (genuinely
  free, no card), or OpenAI / Anthropic / Gemini / NVIDIA / OpenRouter / any
  OpenAI-compatible endpoint. Provider auto-detected from the key prefix. On-device
  Gemini Nano where hardware allows.
- **Privacy by design** — everything lives on-device; keys in EncryptedSharedPreferences;
  ⟦redaction⟧ markers strip content out of every AI call; notes go only to the provider
  *you* chose.
- **AI everywhere it helps** — editor assistant (edits stream into the note live), bulk
  multi-note edits, a terminal with ~30 real slash commands, a Socratic tutor grounded in
  your notes, an AI graph maker, daily roadmap, ambient micro-lessons.
- **Undo everything** — shared per-note history across editor, terminal and bulk edits,
  plus persisted version snapshots with one-tap restore.
- **Fast** — FTS4 full-text search, quick switcher, spatial-grid graph physics that
  freezes at rest, warm-start caches, baseline-profile-ready.

## Stack

- **Kotlin + Jetpack Compose** (Material 3), single-activity, native Android
- **Room** (SQLite + FTS4) · **DataStore** · **Hilt** · **Coroutines/Flow** · **WorkManager**
- **OkHttp + kotlinx.serialization** for the multi-provider LLM layer (streaming SSE)
- **Glance** home-screen widget · **SplashScreen** API · Android TextToSpeech
- ~120 JVM unit tests over the pure core (FSRS, markdown, commands, physics helpers)

Min SDK 26 · Target/Compile SDK 35 · JDK 17 · AGP 8.7 · Kotlin 2.0

## Build & run

> Open directly in **Android Studio** (Ladybug+), or with a command-line SDK:

```bash
cp local.properties.example local.properties   # set sdk.dir
./gradlew testDebugUnitTest                    # run the unit-test suite
./gradlew assembleDebug                        # build the debug APK
./gradlew installDebug                         # install on a device/emulator
```

No API keys in config — AI keys are entered **in-app** (BYOK, encrypted on-device).
Optional Google sign-in needs `GOOGLE_SERVER_CLIENT_ID` in `local.properties`
(see `.env.example`).

## Project layout

```
app/src/main/java/com/generic/mnemos/
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

## Status

Active daily development. Licensed under **AGPL-3.0** — see [`LICENSE`](LICENSE).
Security model and vulnerability reporting: see [`SECURITY.md`](SECURITY.md).
Contributions welcome: see [`CONTRIBUTING.md`](CONTRIBUTING.md).
