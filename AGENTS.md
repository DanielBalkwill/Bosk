# AGENTS.md — Bosk codebase guide (for AI agents & reviewers)

> This file is a machine-oriented map of the Bosk codebase: an AI coding agent or reviewer
> can read it to understand the architecture, locate any subsystem, follow the main data
> flows, and respect the project's conventions. It is self-contained enough to analyse the
> project without the full source. Human-facing overview lives in `README.md`; the "why"
> behind decisions lives in `docs/ARCHITECTURE.md`; the build log / feature history lives in
> `docs/CHECKPOINTS.md`.

## 1. What Bosk is

Bosk is a **native Android, local-first AI learning app** (Kotlin + Jetpack Compose). It
fuses four pillars behind an AI that acts as the connective tissue:

1. **Knowledge graph** (Obsidian-style `[[wiki-links]]` → a live force-directed graph).
2. **Spaced repetition** (a from-scratch FSRS-4.5 scheduler; cloze cards; Anki `.apkg` import).
3. **Live-preview Markdown editing** (rendered in place, caret line raw; version history).
4. **Ambient companion** (a background daemon + lock-screen overlay that surfaces micro-lessons).

Design thesis: **"the graph is the curriculum."** The Room-backed knowledge graph is the
single source of truth; a curriculum engine reads the graph + spaced-repetition state to
decide what to resurface next.

## 2. Quick facts

| Property | Value |
|---|---|
| Language / UI | Kotlin 2.0 · Jetpack Compose (Material 3), single-activity |
| Package / applicationId | `app.bosk` |
| Source size | ~23k LOC, 154 Kotlin files in `app/src/main` |
| Tests | 136 JVM unit tests across 18 files (`app/src/test`) over the pure `core/` |
| Persistence | Room (SQLite + FTS4), DB name `bosk.db`, **schema v7** · DataStore for prefs |
| DI | Hilt, rooted in `BoskApplication` |
| Async | Coroutines + Flow; **nothing blocks the main thread** |
| Networking | OkHttp + kotlinx.serialization (streaming SSE for LLMs) |
| Min / Target SDK | 26 / 35 · JDK 17 · AGP 8.7 |
| CI | GitHub Actions (`.github/workflows/android.yml`) — compile + unit tests on every push |
| Licence | AGPL-3.0 |

## 3. Build, test, run

```bash
cp local.properties.example local.properties   # set sdk.dir (Android Studio writes this)
./gradlew testDebugUnitTest                     # run the ~136-test JVM unit suite
./gradlew assembleDebug                          # build the debug APK
./gradlew installDebug                           # install on a device/emulator
```

- No API keys in config. AI keys are entered **in-app at runtime** (BYOK, encrypted on-device).
- Optional Google Sign-In needs `GOOGLE_SERVER_CLIENT_ID` in `local.properties` (see `.env.example`).
- Room schemas are exported to `app/schemas/app.bosk.core.data.BoskDatabase/` (committed, used by migration tests).

## 4. Architecture principles (respect these)

- **Three layers, one dependency rule — `core/` never depends on Compose.**
  `core/` = pure logic + data (unit-testable off-device); `ui/` = Compose + ViewModels;
  `service/` = background / permission-heavy code.
- **MVVM.** Screens are stateless Composables that observe `StateFlow` from a `@HiltViewModel`.
  Derived read models use `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), …)`.
- **Everything async is `suspend`/`Flow`.** No AI/network/DB call on the main thread. LLM
  output is a `Flow<String>` so TTS/UI can consume tokens as they stream.
- **No hardcoded colours/dimensions in screens** — use tokens in `ui/theme/`.
- **No secrets in source**, ever (see §9). Keys are runtime-only; build secrets come from
  the gitignored `local.properties`.
- **Checkpoints:** substantial work is appended as a `## CPn — title` manifest in
  `docs/CHECKPOINTS.md` (currently at CP45). Follow that convention for new work.

## 5. Module map (`app/src/main/java/app/bosk/`)

### `core/` — pure logic + data (no Compose)
- `ai/` — LLM + note-AI layer.
  - `ai/llm/` — `LlmRouter` (fronts providers), `LlmClient`, `LlmProvider` (interface),
    `ProviderId` (enum + **key-prefix auto-detection**), `ProviderKey`, `OpenAiCompatPreset`
    (Groq/OpenRouter/DeepSeek/custom presets), and providers: `GeminiProvider`,
    `GeminiNanoProvider` (on-device, keyless), `OpenAiProvider`, `AnthropicProvider`,
    `OpenAiCompatibleProvider`.
  - `ai/model/` — serialization DTOs (`GeminiDtos`, `ProviderDtos`).
  - `NoteAiService` (editor assistant, card generation), `BulkEditService` (multi-note edits),
    `Persona` / `PersonaManager` (customisable AI persona merged into system prompts),
    `KnowledgeProfile` / `KnowledgeProfileManager(+Impl)` / `KnowledgeProfileUpdater`
    (the "Mulch pattern" context block prepended to every call), `Roadmap` (daily LLM roadmap),
    `PromptLibrary`, `RpmRateLimiter`, `RecentCommandsStore`.
- `data/` — Room. `BoskDatabase` (v7), `Converters`; `data/dao/` (7 DAOs); `data/entity/` (§6).
- `notes/` — `NotesRepository(+Impl)`, `Markdown` + `MarkdownEdit` (model/edits),
  `WikiLinks` + `WikiLinkSyncer` (link→edge sync, ghost nodes), **`Redaction`** (strips
  `⟦…⟧` before AI), `EditHistory` / `NoteEditHistories` (shared undo), `Frontmatter`,
  `Mentions`, `ActiveNoteStore`, `CompilerStore` (the study "queue"), `ImageStore`,
  `ObsidianImporter`, `VaultExporter`.
- `review/` — `ReviewRepository`, `ClozeCards`, `AnkiImporter`, `KnowledgeHealth`, `Streak`.
- `srs/` — **`Fsrs`**: pure FSRS-4.5 scheduler, `fsrsSchedule(card, rating, now) → card`, no side effects.
- `graph/` — `GraphRepository(+Impl)`, `GraphModels`.
- `commands/` — `CommandRegistry`, `CommandNote` (user-editable slash-command doc), `VaultActions`.
- `curriculum/` — `CurriculumProvider` (what the ambient daemon surfaces next).
- `settings/` — `SettingsRepository(+Impl)` (DataStore `bosk_settings`), enums `ThemeMode`,
  `DeliveryMode`, `OverviewLayout`, models `AmbientPrefs`, `GraphGroup`.
- `auth/` — **`SecureKeyStore`** (EncryptedSharedPreferences; §9).
- `queue/` (`QueueRepository`), `cache/` (`SessionCache`, warm-start caches),
  `tts/` (`TtsQueueEngine(+Impl)`, `PlaybackController`), `sync/` (`SyncManager`, paid-tier cloud sync),
  `di/` (`AppModule`, `BindingsModule`, `DataModule`).

### `ui/` — Compose + ViewModels
- `screens/` — one package per screen, each a `*Screen.kt` + `*ViewModel.kt`:
  `home`, `notes` (editor/compiler), `graph` (vault/flashcards/AI-maker), `review` (="Practise"),
  `settings`, `aitutor`, `aipartner`, `aiinterface` (terminal), `onboarding`, `stats`,
  `queue`, `flashcardgraph`.
- `components/` — `QuickSwitcher`, `CommandPaletteRow`, `Haptics`, `NoteCard`,
  `SwipeableNoteRow`, `TagChip`, `ApiKeyField`, `AiResultSheet`.
- `navigation/` — `BoskNavHost` (HorizontalPager tab shell), `Destinations`, `PageVisibility`,
  `NavBadgeViewModel`.
- `theme/` — `Theme` (`BoskTheme`), `Color`, `Type`, `Shape`. `RootViewModel` (top-level
  onboarding + theme state) sits at `ui/RootViewModel.kt`.

### `service/` — background / permissions
- `ambient/` — `AmbientService` (foreground daemon), `AmbientScheduler`, `AmbientWorker` (WorkManager).
- `overlay/` — `OverlayService` (lock-screen overlay via `SYSTEM_ALERT_WINDOW`), `OverlayContent`,
  `OverlayCommandHandler`.
- `notification/` — `NotificationChannels`, `NotificationHelper`.
- `queue/` — `QueueAlarmScheduler` (exact alarms), `QueueAlarmReceiver` (rings/vibrates).
- `widget/` — `BoskWidget` (Glance home-screen widget).

### Entry points
- `MainActivity` (single activity; installs splash, enables edge-to-edge, drives `BoskTheme`
  from settings, routes onboarding vs `BoskNavHost`).
- `BoskApplication` (Hilt root + WorkManager config).
- `ShareReceiverActivity` (Android share-sheet target → new note).

## 6. Data model (Room, `bosk.db`, schema v7)

Entities (`core/data/entity/`): `NoteEntity`, `NoteFtsEntity` (FTS4 search mirror),
`NoteVersionEntity` (history snapshots, v7), `NodeEntity` + `EdgeEntity` (the graph),
`ReviewEntity` (SRS/flashcard state), `KnowledgeProfileEntity`, `SpeechItemEntity`
(TTS queue), `QueueItemEntity` (tasks/alarms/events).
DAOs: `noteDao`, `noteVersionDao`, `graphDao`, `reviewDao`, `knowledgeProfileDao`,
`speechQueueDao`, `queueDao`. Migration history: v5 recycle bin · v6 `notes_fts` · v7 note versions.

## 7. Key data flows

- **Note save → graph.** `NotesRepositoryImpl.upsert` persists the note; `WikiLinkSyncer.sync`
  ensures a `NodeEntity` for the note, parses `[[links]]`/`!refs`, and creates edges — minting
  "ghost" concept nodes for unresolved links so **every link yields a visible edge**.
- **AI request.** Caller → `LlmRouter` (selects provider by stored `selectedProvider`) →
  provider impl. Before sending: `Redaction.forAi(text)` strips `⟦…⟧` spans, and the
  `KnowledgeProfileManager` context block is prepended as system context. Keys are read from
  `SecureKeyStore`. Response streams back as `Flow<String>`.
- **Tutor quiz → real FSRS.** `AiTutorViewModel.startQuiz` pulls **due flashcards first**
  (`ReviewRepository.dueCards`); free-text answers are graded by the model (strict JSON);
  a real card's grade calls `reviews.recordReview(id, Rating)` → `Fsrs` reschedules it;
  misses can be turned into new cards.
- **Ambient surface.** `AmbientWorker` (WorkManager, interval + quiet-hours) asks
  `CurriculumProvider` what to surface, then delivers via `DeliveryMode` (TTS / overlay /
  notification).

## 8. Conventions / do & don't

- **Do** keep `core/` free of Compose/Android-UI imports; put logic there so it's unit-tested.
- **Do** add/extend unit tests in `app/src/test` for `core/` logic changes; CI must stay green.
- **Do** append a checkpoint manifest to `docs/CHECKPOINTS.md` for substantial changes.
- **Do** route colours/dimensions through `ui/theme/` tokens.
- **Don't** add a backend dependency or send user data anywhere except the user's chosen LLM
  provider endpoint. There is no first-party server for the free tier.
- **Don't** log, back up, or transmit API keys (see §9).
- **Don't** block the main thread; wrap provider/DB work in `suspend`/`Flow`.

## 9. Security & privacy model (first-class, see `SECURITY.md`)

- **Keys** live only in `EncryptedSharedPreferences` (`core/auth/SecureKeyStore.kt`):
  AES-256-GCM values, AES-256-SIV key names, master key in the **Android Keystore**; prefs
  file `bosk_secure_prefs`, **excluded from backup & device-transfer** via
  `res/xml/backup_rules.xml` + `res/xml/data_extraction_rules.xml`; never logged.
- **BYOK, no proxy.** Keys/notes are only ever sent to the provider the user chose. Zero AI
  cost, zero custodial server risk.
- **Data minimisation.** `⟦redaction⟧` markers (`core/notes/Redaction.kt`) strip marked
  content out of every outbound AI request.
- **Least privilege.** Only the permissions the features need (`INTERNET`, foreground service,
  overlay, alarms, notifications) — see `AndroidManifest.xml`. Release builds use R8/ProGuard.

## 10. Non-obvious implementation details (for accurate analysis)

- **Pager performance:** `BoskNavHost` reads `pagerState.currentPageOffsetFraction` inside the
  `graphicsLayer{}` draw phase (not composition) so page subtrees don't recompose every scroll
  frame; neighbour page is pre-composed (`beyondViewportPageCount = 1`).
- **Graph physics:** spatial-grid force simulation with cooling/annealing that **freezes at
  rest** — an idle graph costs no frames.
- **Tutor streaming:** replies render raw while streaming and are Markdown-formatted only once
  settled (re-parsing a growing reply every token is O(n²)); auto-scroll follows the stream but
  yields to manual scroll-up; a `streaming` StateFlow is distinct from the broader `busy`.
- **Provider abstraction:** `ProviderId.fromKey(key)` infers the provider from the key prefix
  (`AIza…`→Gemini, `sk-ant-…`→Anthropic, `sk-…`→OpenAI, `gsk_`/`sk-or-`→OpenAI-compatible).
- **Live theming:** `ThemeMode` pref flows DataStore → `RootViewModel` → `MainActivity` → `BoskTheme`.

## 11. Glossary

- **Compiler** — the staging queue of notes selected for study/AI context (`CompilerStore`).
- **Mulch pattern** — the Knowledge Profile context block prepended to each AI call for cheap grounding.
- **Ghost node** — an auto-created concept `NodeEntity` for an unresolved `[[link]]`.
- **Practise** — the spaced-repetition review screen (renamed from "Review").
- **Ambient companion** — the background daemon/overlay that resurfaces material.

## 12. Known limitations / roadmap

Pre-1.0 (`versionName 0.1.0`); schema may still change. On-device Nano needs supported
hardware (Pixel 8+/S24-class). Optional cloud sync (`core/sync`) is the only planned paid
feature and never includes keys. Inline Markdown image rendering and a launcher-icon redesign
(still an "M" glyph) are open. See `docs/CHECKPOINTS.md` for current status.
