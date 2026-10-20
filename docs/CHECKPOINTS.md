# Mnemos — Checkpoint Manifests

The resume mechanism. Each checkpoint appends its manifest here when complete so any
future session can continue without context loss.

---

## CP1 — Project Scaffold & Architecture

```
CP1 STATUS: COMPLETE
Platform chosen: Android Native (Kotlin + Jetpack Compose)
Reasoning: The overlay popup (SYSTEM_ALERT_WINDOW), foreground ambient daemon,
           notification media controls and TTS are deeply native APIs; Material You
           dynamic colour is core to the design — native avoids fighting the RN bridge.

Files created:
  Build system:
    settings.gradle.kts, build.gradle.kts, gradle.properties
    gradle/libs.versions.toml (version catalog — single source of dependency truth)
    gradle/wrapper/ (gradle-wrapper.jar + .properties, Gradle 8.11.1)
    gradlew, gradlew.bat
    app/build.gradle.kts, app/proguard-rules.pro
  Manifest & resources:
    app/src/main/AndroidManifest.xml (INTERNET, FOREGROUND_SERVICE[_MEDIA_PLAYBACK],
      POST_NOTIFICATIONS, SYSTEM_ALERT_WINDOW, WAKE_LOCK, RECEIVE_BOOT_COMPLETED)
    res/values/{strings,colors,themes}.xml
    res/xml/{backup_rules,data_extraction_rules}.xml (exclude the secure prefs)
    res/mipmap-anydpi-v26/ic_launcher{,_round}.xml + res/drawable/ic_launcher_{fg,bg}.xml
  Kotlin — app:
    MnemosApplication.kt (@HiltAndroidApp), MainActivity.kt (@AndroidEntryPoint)
  Kotlin — ui:
    theme/{Color,Type,Shape,Theme}.kt  ("deep ink on paper" + Material You dynamic colour)
    navigation/{Destinations,MnemosNavHost}.kt  (bottom-bar over 5 screens)
    screens/{home,notes,graph,review,settings,onboarding}/*Screen.kt  (navigable placeholders)
    components/PlaceholderScreen.kt
  Kotlin — core (interfaces/stubs with TODO CP markers):
    auth/SecureKeyStore.kt          <-- REAL impl (EncryptedSharedPreferences, BYOK key)
    ai/GeminiClient.kt, ai/KnowledgeProfileManager.kt
    srs/Fsrs.kt (pure-function contract; equations TODO CP2)
    graph/GraphModels.kt, notes/NotesRepository.kt, tts/TtsQueueEngine.kt, sync/SyncManager.kt
    di/AppModule.kt
  Kotlin — service (stubs, TODO CP4):
    ambient/AmbientService.kt, overlay/OverlayService.kt, notification/NotificationChannels.kt
  Config & docs:
    .gitignore, .env.example, local.properties.example
    README.md, docs/ARCHITECTURE.md, docs/CHECKPOINTS.md

Key decisions:
  - Version catalog (libs.versions.toml) for all deps.
  - Hilt for DI; Coroutines/Flow for all async (no blocking AI calls).
  - SecureKeyStore implemented for real now (key is sacred); prefs file name
    "mnemos_secure_prefs" is wired to the manifest backup-exclusion rules.
  - Fonts: system serif/sans fallbacks in CP1; real DM Serif/DM Sans via downloadable
    Google Fonts deferred to CP3 (avoided shipping unverified font cert hashes).
  - Services kept as plain stubs (not yet android.app.Service) so the manifest stays
    buildable until CP4 implements + registers them.

Verification:
  - All XML well-formed; Gradle wrapper bootstraps; ./gradlew help + :app:help OK.
  - SDK GAP CLOSED: installed cmdline-tools + platform-35 + build-tools;35.0.0 at
    ~/Android/Sdk (local.properties -> sdk.dir). Real compile + 20MB app-debug.apk built.
  - One real bug caught by the compiler and fixed: SecureKeyStore KDoc contained
    "res/xml/*_rules.xml" — the "/*" opened a NESTED Kotlin block comment that swallowed
    the closing */, leaving an unclosed comment. Reworded. (Watch for /* in KDoc.)

Next: CP2 — Data Layer & AI Integration
```

---

## CP2 — Data Layer & AI Integration

```
CP2 STATUS: COMPLETE

Database schema (Room, version 1, schema exported to app/schemas/):
  entity/  NoteEntity, NodeEntity, EdgeEntity, ReviewEntity, KnowledgeProfileEntity (singleton)
  dao/     NoteDao, GraphDao (nodes+edges, incl. orphan/edge-count queries), ReviewDao
           (due-queue + observeDueCount), KnowledgeProfileDao
  Converters (List<String> tags <-> JSON), MnemosDatabase, NotesRepositoryImpl (entity<->Note)
  FK cascades wired; indices on updatedAt / noteId / dueDate / source+targetNodeId.
  DEVIATION (rule #6): ReviewEntity uses FSRS stability/difficulty, NOT SM-2 interval/ease.

FSRS implemented: YES — core/srs/Fsrs.kt, full FSRS-4.5 as PURE functions
  (fsrsSchedule + retrievability), 17 default weights, power forgetting curve.
  7 unit tests in app/src/test (FsrsTest) — ALL PASS (ordering, lapses, clamping,
  interval≈stability@0.9, monotonic due dates, retrievability decay).

Gemini client: YES — core/ai/GeminiClientImpl (gemini-2.0-flash, BYOK key from
  SecureKeyStore, only ever sent to generativelanguage.googleapis.com).
  - generate() Result<String>, generateStream() Flow<String> (SSE, alt=sse), validateKey()
  - RpmRateLimiter (sliding 60s window, 15 RPM, suspends not fails)
  - exponential backoff + jitter on 429/503/500
  - DTOs in core/ai/model (kotlinx.serialization)
  - NO http logging interceptor on purpose (key is in URL — rule #5)

Knowledge profile: KnowledgeProfile (strong/weak/active topics, totalCards, retentionRate)
  + KnowledgeProfileManagerImpl (load/render/save/update) backed by singleton Room row.
  currentContextBlock() returns "" until a profile exists (no noisy preamble). Mulch
  pattern: prepended as Gemini systemInstruction. Richer updates deferred to CP5.

Prompt templates: 5 .md files in app/src/main/assets/prompts/ (summarise_note, connect_nodes,
  generate_card, explain_concept, curriculum_next) + PromptLibrary (cached asset loader,
  {{placeholder}} substitution). DEVIATION: assets/ not /core/ai/prompts/ so they're
  runtime-loadable on Android; still plain editable .md.

DI: DataModule (db, daos, OkHttp, Json) + BindingsModule (@Binds NotesRepository,
  GeminiClient, KnowledgeProfileManager). SecureKeyStore self-provides.

Verification: ./gradlew compileDebugKotlin OK · testDebugUnitTest 7/7 PASS ·
  assembleDebug OK (20MB APK) · Room schema 1.json exported. Built against real SDK.

NOT in CP2 (correctly deferred): UI to drive any of this (CP3), instrumented Room tests
  on a device (no emulator here), live Gemini call with a real key (BYOK at runtime).

Next: CP3 — Notes Editor & Knowledge Graph UI
```

---

## CP3 — Notes Editor & Knowledge Graph UI

```
CP3 STATUS: COMPLETE  (graph renderer kept intentionally simple per Daniel's steer)

Notes editor:
  ui/screens/notes/NotesViewModel + NotesScreen — card list, search (filters live),
    FAB to create, swipe LEFT = archive/delete, swipe RIGHT = mark-for-review (snaps back,
    real action in CP5). components/NoteCard + TagChip + SwipeBackground.
  NoteEditorViewModel + NoteEditorScreen — title/body/tags fields, 700ms debounced
    AUTOSAVE (+ save on back), inline #tag parsing merged with the tags field. Creating/
    saving a note auto-ensures a graph node (GraphRepository.ensureNodeForNote).
  AI actions row (AssistChips): Summarise / Cards / Connections / Explain -> NoteAiService,
    results shown in AiResultSheet (ModalBottomSheet: loading / result / friendly error).

Knowledge graph:
  ui/screens/graph/GraphViewModel + GraphScreen — Compose Canvas. Nodes = circles sized by
    degree; edges = lines with strength-driven opacity (AI-suggested edges thinner). Pan +
    pinch-zoom (detectTransformGestures), tap-to-select (hit-test), orphan nodes ringed in
    error colour, selected node ringed in accent. Selected-node card: Suggest(AI) /
    Confirm suggestions / Delete node / Close. Snackbar for AI results.

Data/use-case added:
  core/graph/GraphRepository(+Impl) — observeGraph (combines nodes+edges, computes degree),
    ensureNodeForNote (spiral layout), addEdge/confirmEdge/deleteEdge/deleteNode.
  core/review/ReviewRepository(+Impl) — addCard (new card due now), observeDueCount.
  core/ai/NoteAiService — summarise/explain/generateCards(parse JSON->ReviewEntities)/
    findConnections(compare to <=5 notes -> AI-suggested edges). JSON fence-stripping.
  GraphDao extended (nodeById/edgeById/nodeCount). BindingsModule binds Graph+Review repos.

Navigation: bottom bar over 5 screens; full-screen note_editor/{noteId} (-1 = new), bar
  hidden on editor. Routes.NOTE_EDITOR_BASE.

Verification: compileDebugKotlin OK · assembleDebug OK (20MB APK, full Hilt+ViewModel
  graph resolves) · testDebugUnitTest 7/7 (no regression). Built against real SDK.

Wired prompt templates: SUMMARISE_NOTE, GENERATE_CARD, CONNECT_NODES, EXPLAIN_CONCEPT
  (CURRICULUM_NEXT waits for CP5).

DEFERRED (documented, not silent):
  - Markdown is plain-text editing + #tag parsing; NO formatting toolbar / rendered preview
    yet (TODO CP3-polish / CP6). Per "keep it simple at this stage".
  - Search uses LIKE (NoteDao); FTS4 deferred to a later polish pass.
  - Manual drag-to-connect + node merge deferred; connections are AI-suggested then
    confirmed. Filter-by-tag/due view deferred (orphan highlight IS implemented).
  - No emulator on the build box, so screens compiled + packaged but not visually run here.
    AI actions need a real Gemini key at runtime (BYOK entered in CP5 onboarding).

Next: CP4 — TTS Engine, Ambient Daemon & Overlay System
```

---

## CP4 — TTS Engine, Ambient Daemon & Overlay System

```
CP4 STATUS: COMPLETE

TTS engine:
  core/tts/PlaybackController — real Android TextToSpeech: async init, sentence chunking
    (~3.5k cap), speech rate 0.5–2.0x, audio focus (transient, ducks for calls), onDone cb.
  core/tts/TtsQueueEngine(+Impl) — persistent PRIORITY queue (Room: speech_queue, DB->v2),
    play/pause/skip/stop/reorder, StateFlow<TtsState> (currentText/isPlaying/queueSize).
  SpeechItemEntity + SpeechQueueDao (priority DESC, createdAt ASC). DB version 1->2.

Ambient daemon:
  service/ambient/AmbientService — foreground service (FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK),
    hosts TtsQueueEngine, syncs Now Playing notification to playback state, handles intents
    PLAY/PAUSE/SKIP/STOP/SPEAK/SURFACE. Started via ContextCompat.startForegroundService.
  service/ambient/AmbientWorker (@HiltWorker, CoroutineWorker) — respects enabled + quiet
    hours, asks CurriculumProvider.next(), calls AmbientService.surface().
  service/ambient/AmbientScheduler — WorkManager unique periodic work, battery-not-low
    constraint, 15-min floor. MnemosApplication is Configuration.Provider (HiltWorkerFactory)
    + collects settings.prefs to reschedule; default WM initializer removed in manifest.
  core/curriculum/CurriculumProvider(+Impl) — deterministic priority (overdue card ->
    recent note -> orphan). AI CURRICULUM_NEXT layering deferred to CP5.
  core/settings/SettingsRepository (DataStore) — AmbientPrefs (enabled, interval, quiet
    hours w/ midnight wrap, DeliveryMode TTS|OVERLAY|NOTIFICATION, ttsSpeed, voice).

Overlay popup (flagship):
  service/overlay/OverlayService — SYSTEM_ALERT_WINDOW (TYPE_APPLICATION_OVERLAY). Service
    implements LifecycleOwner/ViewModelStoreOwner/SavedStateRegistryOwner and wires them to a
    ComposeView so Compose runs in the window. Dim-behind, focusable (keyboard), single instance.
  service/overlay/OverlayContent — Compose card: title/body, command TextField, STREAMS the
    Gemini reply inline, close/skip dismiss.
  service/overlay/OverlayCommandHandler — freeform + "explain more" stream via gemini; "skip/
    dismiss/close" recognised. STRUCTURED side-effects (mark-known/remind-tomorrow/add-
    connection) recognised but effects deferred to CP5 (need target ids on the surfaced item).

Notifications: service/notification/NotificationHelper — channels (Now Playing LOW, Ambient
  DEFAULT), podcast-style ongoing notification with play/pause + skip PendingIntents, BigText
  ambient nudges.

Manifest: AmbientService (foregroundServiceType=mediaPlayback) + OverlayService registered;
  androidx.startup WorkManagerInitializer removed (tools:node=remove).

Verification: compileDebugKotlin OK · assembleDebug OK (21MB APK, @HiltWorker + foreground/
  overlay services + WorkManager-via-Hilt all resolve) · testDebugUnitTest 9/9 (added 2
  quiet-hours wrap tests) · Room schema 2.json exported. Built against real SDK.

DEFERRED / not reachable yet (documented, not silent):
  - No UI yet to enable ambient, request SYSTEM_ALERT_WINDOW / POST_NOTIFICATIONS, choose
    delivery mode, or fire a manual/shake/on-unlock trigger — all that is CP5 (Settings +
    onboarding + permissions). Overlay no-ops gracefully until the permission is granted.
  - Auto-dismiss timeout + swipe-down dismiss on the overlay: TODO (close button + "skip" work).
  - Chatterbox premium voice: stub (CP6). AI-summarised surfacing (vs deterministic text): CP5.
  - Couldn't runtime-verify on a device from this box (no emulator here). Daniel tests on device.

Next: CP5 — Review UI, Curriculum Engine & Onboarding
```

---

## CP5 — Review UI, Curriculum Engine & Onboarding

```
CP5 STATUS: COMPLETE  (closes the "no way to enter the Gemini key" gap)

Review UI:
  ui/screens/review/ReviewViewModel + ReviewScreen — due-card queue, tap-to-reveal
    (Crossfade flip), Again/Hard/Good/Easy rating buttons, progress, session summary
    (reviewed count + ~retention %), manual card creation (AlertDialog -> attaches to a
    reusable "Quick Cards" holder note). FloatingActionButton to add.
  Home: ui/screens/home/HomeViewModel + HomeScreen — big due-count, Start review,
    note count. Due-count BADGE on the Review nav tab (NavBadgeViewModel + BadgedBox).

Review domain (FSRS wired):
  ReviewRepository.recordReview(id, rating) — loads ReviewEntity -> FsrsCard ->
    fsrsSchedule() -> writes back stability/difficulty/due/reps/lapses. dueCards(), totalCards().

Curriculum engine:
  core/curriculum/Curriculum.order(...) — PURE, unit-tested ordering (due -> orphans ->
    recent). CurriculumProvider.nextBatch()/next() gather DAO data + apply it. AmbientService
    uses it. (High-connectivity ranking + AI CURRICULUM_NEXT synthesis still a refinement.)
  core/ai/KnowledgeProfileUpdater — after each session, derives strong/weak/active topics
    from reviewed notes' tags, updates totalCards + rolling retentionRate (Mulch profile).

Onboarding (5 steps, ui/screens/onboarding):
  Welcome -> Google (optional, see deferral) -> Gemini API key (ApiKeyField: paste ->
    SecureKeyStore -> gemini.validateKey() live test) -> First note -> Permissions
    (POST_NOTIFICATIONS launcher + overlay settings intent). HorizontalPager + dots.
  Gate: SettingsRepository.onboardingComplete (DataStore) drives RootViewModel; MainActivity
    shows OnboardingScreen until finished, then MnemosNavHost.

Settings (now fully functional — also makes CP4 reachable):
  ui/screens/settings/SettingsViewModel + SettingsScreen — Gemini key (ApiKeyField, shared
    with onboarding), ambient enable + interval chips (15/30/60) + delivery chips
    (TTS/Overlay/Notification) + voice-speed slider + quiet-hours steppers, permission
    buttons (overlay + notifications), and "Speak a test" / "Surface now" triggers.
  ui/components/ApiKeyField (masked input, Save & test, status) — the BYOK on-ramp.

Verification: compileDebugKotlin OK · assembleDebug OK (21MB APK, full Hilt+ViewModel graph)
  · testDebugUnitTest 12/12 (added 3 Curriculum ordering tests). Built against real SDK.

GAP CLOSED: Gemini key is now enterable + validated + stored (onboarding step 3 AND
  Settings). AI features (Summarise/Connections/overlay/etc.) become usable on device.

DEFERRED (documented, not silent):
  - Google sign-in: optional info step only. Real Credential Manager auth deferred — it's
    identity-only (BYOK needs no backend), needs a configured GOOGLE_SERVER_CLIENT_ID (blank
    here), and can't be verified on this box. (deps already present for when wired.)
  - Manual cards attach to an auto-created "Quick Cards" note (ReviewEntity.noteId is non-null).
  - Curriculum: high-connectivity ranking + AI synthesis moments still pending; ambient
    surfacing still speaks deterministic text (AI-summarised surfacing = refinement).
  - Markdown still plain-text (no render/toolbar) from CP3. Structured overlay commands still
    pending from CP4. No emulator here -> screens compiled + packaged, not visually run.

Next: CP6 — Monetisation, Polish & Release Prep
```

---

## CP6 — Multi-Provider AI (Gemini · OpenAI · Claude · on-device Nano)

```
CP6 STATUS: COMPLETE (2026-06-04)
Goal: Break the single-vendor lock-in. BYOK now spans Gemini, OpenAI and Claude — paste ANY
  key and the app detects which provider it is from the prefix — plus a keyless on-device
  free tier (Gemini Nano via AICore). (Monetisation deferred — was the old CP6; pushed back.)

New package: core/ai/llm/
  ProviderId.kt        enum GEMINI/OPENAI/ANTHROPIC/NANO (+ needsKey flag); fromKey() prefix
                       detection: sk-ant- -> Claude (checked first), sk- -> OpenAI,
                       AIza -> Gemini, else null. 6 unit tests (ProviderIdTest).
  LlmClient.kt         consumer-facing interface (generate/generateStream/validateKey +
                       activeProvider) + LlmException(retryable). Replaces old GeminiClient.
  LlmProvider.kt       per-backend interface (id, isConfigured, generate/stream/validate).
  LlmRouter.kt         @Singleton LlmClient; injects Map<ProviderId, LlmProvider> (Hilt
                       multibinding). active() = explicit selectedProvider (honours keyless
                       Nano) else first keyed provider with a key. "paste a key -> it routes."
  ProviderKey.kt       @MapKey annotation for the multibinding.
  GeminiProvider.kt    refactor of the old GeminiClientImpl (gemini-2.0-flash, 429 NON-retry,
                       RpmRateLimiter 15rpm, SSE stream). Uses LlmException now.
  OpenAiProvider.kt    /v1/chat/completions, Bearer auth, model gpt-4o-mini, SSE delta stream,
                       validate via GET /v1/models.
  AnthropicProvider.kt /v1/messages, x-api-key + anthropic-version 2023-06-01, model
                       claude-3-5-haiku-latest, system as top-level field, content_block_delta
                       SSE stream, validate via GET /v1/models.
  GeminiNanoProvider.kt on-device via com.google.ai.edge.aicore. prepareInferenceEngine() +
                       generateContent / generateContentStream. ALL wrapped so any
                       Throwable (incl. VerifyError/NoClassDefFound on unsupported devices)
                       becomes a friendly LlmException. isConfigured()=true (verdict at call).
  model/ProviderDtos.kt OpenAI + Anthropic request/response/stream DTOs (kotlinx.serialization).

Key storage: SecureKeyStore now multi-key — key(provider)/setKey(provider)/hasKey(provider),
  hasAnyKey(), isReady() (=hasAnyKey || selectedProvider==NANO), and persisted selectedProvider.
  GEMINI slot keeps legacy pref name "gemini_api_key" so existing keys survive the upgrade.

DI: BindingsModule binds LlmRouter->LlmClient and @IntoMap each provider; old bindGeminiClient
  removed. Consumers (NoteAiService, OverlayCommandHandler, Settings/OnboardingViewModel) now
  inject LlmClient. Old GeminiClient.kt + GeminiClientImpl.kt DELETED.

UI: ApiKeyField is provider-agnostic (label "AI API key", live "Detected: <provider>" /
  "Unrecognised key format" supporting text). Settings "AI provider" section shows
  "Active: <provider>" + an "Use on-device AI (free, no key)" OutlinedButton (viewModel
  .useOnDeviceAi() -> selectedProvider=NANO). Onboarding step 3 retitled "Connect your AI"
  with the same on-device button. SettingsViewModel exposes activeProvider StateFlow.

Build gotcha FIXED: aicore declares minSdk 31; app stays minSdk 26 via
  `<uses-sdk tools:overrideLibrary="com.google.ai.edge.aicore"/>` in the manifest. Nano is
  therefore unavailable (graceful) on API 26-30, app otherwise unaffected.

Verification: assembleDebug OK (real SDK) · testDebugUnitTest 18/18 (12 prior + 6 ProviderId).
DEFERRED/NOTE: Nano not runnable on this box (no AICore device) — integration is written to
  the documented exp01 API + fails safe; confirm on a Pixel 8+/S24+. Model IDs (gpt-4o-mini,
  claude-3-5-haiku-latest) are single-constant swaps. No per-provider model picker UI yet.

Next: CP7 — Home screen redesign (warm-red dark minimalism, DM fonts, 5-zone home).
```

---

## CP7 — Home Screen Redesign (warm-red dark minimalism)

```
CP7 STATUS: COMPLETE (2026-06-04)
Goal: Translate the "refined dark minimalism with a warm red pulse" design spec (supplied as
  an HTML mockup) into native Compose. "A premium notebook that breathes."

Palette (theme/Color.kt — fixed, dynamic colour now OFF in Theme.kt so the brand is identical
  on every device): bg #0D0B0B / surface #161213 / surface2 #1F1819 / border #2E2326 /
  text #F5EFEF / text-secondary #9E8C8E / accent #C94040 / accent-light #E87070 /
  accent-glow 15% red. Mapped onto darkColorScheme (accent=primary, glow=primaryContainer).
  Theme.kt MnemosTheme(dynamicColor = false) by default.

Fonts (theme/Type.kt) — bundled TTFs in res/font/ (offline, deterministic; NOT downloadable):
  dm_serif_display_regular.ttf (titles), dm_sans_variable.ttf (UI/body, weights 300/400/500/
  600 via FontVariation), jetbrains_mono_variable.ttf (mono 400/500). Needs
  @file:OptIn(ExperimentalTextApi) for FontVariation. Pulled from github.com/google/fonts.

Home (ui/screens/home/HomeScreen.kt — full rebuild) — 5 zones, all real-data where cheap:
  1. AI Command bar — pill (surface2 + border), animated 3-bar Waveform (alive while busy),
     BasicTextField, circular accent send button. Routes free text to the active provider via
     HomeViewModel.runCommand -> llm.generate -> AmbientService.speak; reply shows in a card.
  2. Now Playing + live graph — left: recent-note title (serif) + Replay/Play-Pause/Skip wired
     to AmbientService.play/pause/skip (helpers added to its companion). Right: MiniGraph
     (Canvas, 7 organic nodes, accent + white-50% fills, ONE pulsing node w/ expanding glow via
     infiniteTransition) + "View Full Graph →" -> onOpenGraph.
  3. Now Reading — surface card, 3dp left accent rail, "NOW READING" small-caps + note icon,
     serif title, body preview with bottom fade-mask gradient, outline chips Generate Cards /
     Add Connection -> onOpenNote(id).
  4. Today's Queue — "N due" accent pill, up to 3 due cards (reviews.dueCards) with coloured
     status dot + Overdue/Due pill, staggered fade-up entrance (80ms/item). Empty -> caught-up.
  5. Bottom nav (in MnemosNavHost) — restyled NavigationBarItemDefaults.colors (selected =
     primary, indicator = accent-glow). Tabs unchanged (Home/Notes/Graph/Review/Settings).

HomeViewModel rebuilt: injects LlmClient + ApplicationContext; combines observeDueCount +
  observeNotes -> dueCount/noteCount/nowReading(recent note)/queue; runCommand/dismissReply/
  play/pause/skip. NavHost passes onStartReview/onOpenNote/onOpenGraph to HomeScreen.

Verification: assembleDebug OK (real SDK, 22.9MB APK; fonts + aicore confirmed packaged) ·
  testDebugUnitTest 18/18. No device attached this session -> NOT visually run on hardware.
DEVIATIONS/NOTES: nav tabs kept as-is (design's Calendar/Flashcards labels not adopted — would
  break feature mapping; Review≈Flashcards). Play/Pause is optimistic local state (no TTS
  progress tracking) — controls fire real service intents. Progress-bar with numeric times
  omitted (no real elapsed/total to show) in favour of honest controls.

Remaining checkpoint: CP8 — Monetisation, Polish & Release Prep (still deferred by Daniel).
```

---

## CP6.1 — Universal OpenAI-compatible provider (Groq · OpenRouter · DeepSeek · custom)

```
CP6.1 STATUS: COMPLETE (2026-06-04)
Why: Daniel's phone can't run Gemini Nano, and he wanted a free/no-billing path. DeepSeek
  (his idea) is OpenAI-compatible but pay-as-you-go — NOT free. The genuinely-free, no-card
  keys are Groq + OpenRouter (free models). All of these speak /chat/completions, so ONE
  provider reaches them all.

Added:
  ProviderId.OPENAI_COMPAT (needsKey). fromKey() now: sk-or-→COMPAT (OpenRouter),
    gsk_→COMPAT (Groq), sk-ant-→Claude, sk-→OpenAI, AIza→Gemini. (DeepSeek's sk- collides
    with OpenAI → reached via explicit preset, not paste-detection.)
  OpenAiCompatPreset enum: OPENROUTER/GROQ/DEEPSEEK/CUSTOM, each with baseUrl + defaultModel
    + signupUrl + free flag; fromKey() auto-detects OpenRouter/Groq.
  OpenAiCompatibleProvider (LlmProvider, id=OPENAI_COMPAT) — reuses OpenAI DTOs; baseUrl +
    model + key all from SecureKeyStore (compatBaseUrl/compatModel slots added). validateKey
    = a 1-token generation (portable across backends). Sends X-Title: Mnemos (OpenRouter).
  DI: @IntoMap binding added.
  SettingsViewModel: paste of sk-or-/gsk_ auto-sets base+model. New selectCompatPreset /
    setCompatBaseUrl / setCompatModel / saveCompatKey + compatBaseUrl/compatModel StateFlows.
  SettingsScreen: "Free & custom AI (OpenAI-compatible)" section — preset FilterChips + signup
    hint + editable Base URL/Model + masked key field + "Save & use this provider".
  OnboardingViewModel: same auto-config branch (a Groq/OpenRouter key pasted in onboarding works).
  ApiKeyField: detection text now names the preset ("Detected: Groq (free)" / "OpenRouter (free)").

Defaults: OpenRouter → deepseek/deepseek-chat-v3-0324:free ; Groq → llama-3.3-70b-versatile ;
  DeepSeek → deepseek-chat. (All single-constant swaps in OpenAiCompatPreset.)

Verification: assembleDebug OK · testDebugUnitTest 21/21 (added 3 compat-detection tests).
Note for Daniel: cross-device on-device (MediaPipe + bundled small model) was offered as the
  alternative to Nano but NOT chosen this round — still open if he wants truly-offline-anywhere.
```

---

## CP6.2 — More free no-card presets (Cerebras · NVIDIA NIM) + UK/Gemini fact

```
CP6.2 STATUS: COMPLETE (2026-06-05)
Verified (web): Gemini free tier is NOT available in UK/EEA/CH — Google requires the PAID tier
  there (data-protection), and Google moves all users to PREPAY from 2026-03-23. So "free Gemini"
  and "billing-but-no-prepay" are both dead ends for Daniel (UK). The working answer = free,
  no-card, NON-geo-restricted OpenAI-compatible backends.

Added to OpenAiCompatPreset (free=true): CEREBRAS (api.cerebras.ai/v1, llama-3.3-70b, ~1M tok/day)
  and NVIDIA NIM (integrate.api.nvidia.com/v1, meta/llama-3.3-70b-instruct). Paste-detection +
  preset-detection extended: csk-→Cerebras, nvapi-→NVIDIA (alongside sk-or-→OpenRouter,
  gsk_→Groq). Full free roster now: Groq (14.4k req/day, fastest), Cerebras, NVIDIA, OpenRouter
  (capped). DeepSeek+OpenRouter are pay-leaning; Groq/Cerebras/NVIDIA are the truly-free picks.
Verification: assembleDebug OK · 21/21 tests (extended compat-detection asserts).
Recommendation given to Daniel: Groq first (free, no card, works in UK, fastest), Cerebras backup.
```

---

## CP8 — Obsidian-style [[wiki-links]] → graph edges

```
CP8 STATUS: COMPLETE (2026-06-05)
Goal: typing [[Note Name]] (or ![[Name]] embed) in a note body creates a real connection on
  the graph — Obsidian's core linking mechanic.

core/notes/WikiLinks.kt — pure parser. refs(text)->List<Ref(name,embed,start,end)>;
  names(text)->distinct case-insensitive names. Strips alias [[A|x]] and heading [[A#h]].
  6 unit tests (WikiLinksTest).
core/notes/WikiLinkSyncer.kt (@Singleton) — sync(note): parse names, ensure source node,
  clearWikiLinksFrom(sourceNode), resolve each name->note by title (case-insensitive) from
  notes.observeNotes().first(), addWikiLinkEdge per resolved target (self-links/unresolved
  skipped). Text is the source of truth — re-derived every save.
Data: EdgeEntity.wikiLink:Boolean added; DB v2->v3 (fallbackToDestructiveMigration; schema
  3.json exported). GraphDao.clearWikiLinksFrom(sourceId). GraphRepository.clearWikiLinksFrom
  + addWikiLinkEdge (solid: strength 1, aiGenerated=false, wikiLink=true, label "links to").
  GraphEdge.wikiLink surfaced.
Wire-in: NoteEditorViewModel injects WikiLinkSyncer; save() calls wikiLinks.sync(note) (also
  ensures the node) instead of bare ensureNodeForNote. connectionCount already reactive → counts.
Verification: assembleDebug OK · 27/27 tests.
DEFERRED: editor doesn't yet colour [[links]] or autocomplete on "[[" (see CP10's rich input,
  reusable); embed transclusion (![[ ]]) currently links but isn't rendered inline.
```

---

## CP9 — Force-directed "elastic blob" graph + real home preview

```
CP9 STATUS: COMPLETE (2026-06-05)
Goal: graph nodes drift like Obsidian (repel + spring + centre + jitter); the home mini-graph
  shows the ACTUAL current note + its 1-hop neighbours (was a fake decorative constellation).

ui/screens/graph/GraphPhysics.kt — rememberForceLayout(nodeIds, edges, seed, running):
  SnapshotStateMap<Long,Offset> updated on withFrameNanos. step(): pairwise repulsion
  (REPULSION/d²), edge springs (rest 95, k .025), centre pull (.013), damping .82, velocity
  clamp (MAX_SPEED 22) + small per-frame jitter (.6) for the alive feel. Seeds new nodes,
  prunes departed. O(n²)/frame (fine for note-scale graphs; only the Canvas recomposes).
Graph tab (GraphScreen): screenOf(n) now reads simPos[n.id] (seeded from node x/y); pan/zoom
  still layered on top; tap hit-test uses live positions. Wiki-link edges render solid.
Home (HomeGraph.kt): subgraph(snapshot,currentNoteId) = centre node (node whose noteId ==
  nowReading.id) + 1-hop neighbours + induced edges; force-laid-out + auto-fit to the small
  panel; centre node pulses (accent), neighbours white-50%. Falls back to whole small graph if
  no centre node yet; placeholder text if empty. HomeViewModel exposes graphSnapshot (inject
  GraphRepository); old fake MiniGraph deleted.
Verification: assembleDebug OK · 27/27 tests. Physics/visuals NOT run on hardware this session.
TUNING NOTE: physics constants in GraphPhysics.kt are best-guess (no device to eyeball) —
  REPULSION/SPRING/DAMPING/JITTER are the knobs if motion feels too lively or too stiff.
```

---

## CP10 — `#note` mentions in the AI command bar

```
CP10 STATUS: COMPLETE (2026-06-05)
Goal: in the home AI command box, `#` opens a note picker; `#Note Name` colours as a token and
  folds that note's contents into the prompt.

core/notes/Mentions.kt — pure:
  find(text, titles) -> List<Match(title,start,end)>: longest-title-first, case-insensitive,
    ends at non-alphanumeric boundary (so #ATP doesn't match inside #ATPase). Handles multi-word
    titles despite # having no closing delimiter.
  activeQuery(text, cursor) -> Query(start,query) or null: nearest # on the line back from cursor.
  suggestions(query, titles) -> prefix matches first, then contains, capped. 7 unit tests.
Command bar (HomeScreen.CommandBar, now TextFieldValue-based):
  - VisualTransformation colours resolved #mentions accent (OffsetMapping.Identity — restyle only).
  - activeQuery → inline suggestion dropdown (Surface list); tap inserts "#Title " at the span and
    moves the cursor; keep typing to filter.
  - placeholder updated: "Ask anything · #note to add a note's contents".
HomeViewModel: exposes allNotes StateFlow (drives titles); runCommand → buildPrompt(text) prepends
  each mentioned note as `Referenced note "T": <body>` before the user text. NoteTitles passed from
  HomeScreen (remembered map).
Verification: assembleDebug OK · 34/34 tests (+7 Mentions).
```

---

## CP11 — Pinch-to-expand the home cards

```
CP11 STATUS: COMPLETE (2026-06-05)
Goal: pinch (two-finger spread) to expand a home card inline — more content, no tab change.

HomeScreen.pinchToExpand(onChange) — Modifier using detectTransformGestures: zoom>1.03 → expand,
  <0.97 → collapse. Two-finger, so it coexists with the page's one-finger verticalScroll.
Applied to:
  - CommandBar: AI reply maxLines 5 → unbounded when expanded (animateContentSize).
  - NowReadingCard: body maxLines 4 → 20, fade-mask hidden, accent rail grows 180→340dp.
  - TodaysQueue: visible items 3 → all; "Pinch to expand · N more" hint when collapsed & >3.
All wrapped in animateContentSize() for smooth height. Per-card `expanded` state (remember).
Verification: assembleDebug OK · 34/34 tests. Gestures NOT exercised on hardware this session.

ALL FOUR QoL CHECKPOINTS (CP8–CP11) COMPLETE. APK 23.0MB. No device attached this session →
  install with: adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## CP12 — Bare `!Note` references → graph edges
- `core/notes/WikiLinks.kt`: added `BareRef`, `bareRefs(text, titles)` and `bareNames(text, titles)`. A bare `!Title` (no brackets) resolves against known note titles, longest-first, case-insensitive, ending at a non-alphanumeric boundary. `![[…]]` embeds are skipped (brackets win).
- `core/notes/WikiLinkSyncer.kt`: `sync()` now snapshots existing notes once and links both bracketed `[[…]]`/`![[…]]` names AND bare `!Title` names (de-duped case-insensitively).
- `ui/screens/notes/NoteEditorScreen.kt`: body `TextField` now colours `[[wiki-links]]` and `!links` via a `VisualTransformation` (accent, identity offset mapping); placeholder mentions all three inline forms.
- `ui/screens/notes/NoteEditorViewModel.kt`: exposes `noteTitles: StateFlow<List<String>>` to drive the highlighting.
- Tests: +4 in `WikiLinksTest` (longest-first, boundary, embed-skip, position/case).

## CP13 — Swipe navigation (HorizontalPager)
- `ui/navigation/MnemosNavHost.kt`: rewritten. A two-route `NavHost` (`main` + note-editor overlay); `main` is a new `MainPager` that hosts all five top destinations in a `HorizontalPager`. Flick left/right between sections; tapping the bottom bar `animateScrollToPage`s smoothly. Clash-Royale depth via `graphicsLayer` scale (0.90→1.0) + alpha (0.5→1.0) keyed on `currentPageOffsetFraction`.
- `ui/navigation/Destinations.kt`: added `Routes.MAIN = "main"`.
- Bottom bar selection now tracks `pagerState.currentPage`; Review badge preserved.

## CP14 — AI compose/edit bar in the note editor
- `core/ai/NoteAiService.kt`: added `composeStream(note, instruction): Flow<String>` — builds/edits the open note from a free-text instruction, streamed, body-only Markdown output, preserving `[[ ]]`/`!` links; uses the knowledge-profile system context (resolved inside a `flow{}` since `currentContextBlock()` is suspend).
- `ui/screens/notes/NoteEditorViewModel.kt`: `EditorState.aiComposing`; `aiCompose(instruction)` cancels the debounced save, streams chunks straight into `state.body` (live rewrite), then `save()` (persist + re-sync links).
- `ui/screens/notes/NoteEditorScreen.kt`: new `AiComposeBar` (OutlinedTextField + Send, AutoAwesome leading icon, IME Send action, spinner while busy) placed above Tags.

Build: `:app:assembleDebug :app:testDebugUnitTest` green — 38/38 unit tests, APK 22.9 MB. Not run on a device this session (none attached).

## CP15 — Rename Review → Practise
- `res/values/strings.xml`: `nav_review` = "Practise". Review screen now has a "Practise" top bar (added in CP20).

## CP16 — Editor AI bar as a top pull-down tab
- `ui/screens/notes/NoteEditorScreen.kt`: replaced the bottom `AiComposeBar` with `AiDropdown` — a tab/handle hanging from the top of the editor; pull down (detectVerticalDragGestures) or tap to reveal the prompt panel (AnimatedVisibility + Surface, bottom-rounded). On send the panel collapses so the live body rewrite is visible. Editor content wrapped in a Box; Column gets top padding for the collapsed handle.

## CP17 — Graph side tabs + swipe disabled on graph
- `ui/navigation/MnemosNavHost.kt`: `HorizontalPager(userScrollEnabled = currentPage != Graph)` so panning the graph no longer flips tabs; passes `onSwipePrev/onSwipeNext` (animateScrollToPage page∓1) into GraphScreen.
- `ui/screens/graph/GraphScreen.kt`: `EdgeTab` composables pinned CenterStart/CenterEnd (chevrons) call those callbacks.

## CP18 — Manual add-node / add-link
- `core/graph/GraphRepository(+Impl)`: `addStandaloneNode(label)` (noteId=null, spiral placement).
- `GraphViewModel`: `addNode(label)` (selects the new node), `linkSelectedTo(targetId)` (addEdge strength 1, aiGenerated=false, non-wikiLink → permanent).
- `GraphScreen`: FAB → `AddNodeDialog`; SelectedNodeCard gains a "Link" button → `LinkPickerDialog` (lists other nodes from the full snapshot).

## CP19 — Graph search / sequester
- `GraphViewModel`: `query` StateFlow + `setQuery`; `visibleSnapshot = combine(graph, notes, query){ filter }` — keeps nodes whose label / linked-note tags / title / body match (case-insensitive), and only edges with both endpoints kept. Blank query → whole graph.
- `GraphScreen`: renders `visibleSnapshot`; `GraphSearchBar` (Surface + OutlinedTextField, clear button) pinned TopCenter. Empty-state copy distinguishes "no match" from "empty graph".

## CP20 — Anki import → Practise
- `core/review/AnkiImporter.kt` (NEW, @Singleton): `import(uri): Result<List<Card>>` on Dispatchers.IO. Detects ZIP via "PK" signature (BufferedInputStream mark/reset). `.apkg` → extract `collection.anki21`/`.anki2` to cacheDir, open read-only SQLite, `SELECT flds FROM notes`, split on 0x1f (field0=front, rest=back), strip HTML/[sound:]. Plain text → tab/comma split per line. New `collection.anki21b` (zstd) unsupported → friendly "re-export with Support older Anki versions".
- `ReviewViewModel`: injects AnkiImporter; `message` StateFlow + `importAnki(uri)` (inserts into "Imported (Anki)" holder note, reloads); `ensureImportedNote()`.
- `ReviewScreen`: "Practise" TopAppBar with a FileDownload action → `OpenDocument(arrayOf("*/*"))` launcher → `importAnki`; SnackbarHost for results.

Build: `:app:assembleDebug :app:testDebugUnitTest` green — 38/38 unit tests, APK 23.5 MB. Not run on a device this session.

## CP21 — Graph physics cooling/annealing
- `ui/screens/graph/GraphPhysics.kt`: `rememberForceLayout` now returns a `ForceLayout(positions, reheat())`. A "temperature" scales the jitter and decays each frame (COOLING 0.97, floor→0) so the blob settles and goes still; once cooled AND at rest it FREEZES (no maths, no writes, no recompositions) until reheated. `step()` takes temperature, returns max displacement.
- `GraphScreen` calls `layout.reheat()` on pan/zoom/tap; `HomeGraph` reheats on tap of the mini-graph. Node-set/edge changes auto-reheat. This also removes the previous perpetual full-canvas recomposition (a real smoothness/battery win).

## CP22 — AI Partner tab
- `ui/navigation/Destinations.kt`: new `TopDestination.AiPartner` (Psychology icon); `strings.xml` `nav_ai_partner` = "AI Partner".
- `ui/screens/aipartner/AiPartnerScreen.kt` (NEW): placeholder ("personas/voice live here"); wired into the pager `when`. Bottom nav is now 6 items.

## CP23 — Anki decks: import, study, download
- `core/review/AnkiImporter.kt`: `import` now returns `List<Deck>` (Deck = name + cards). Parses deck names from the `decks` table or legacy `col.decks` JSON; groups via `cards.did`→`notes` join. Plain text → one "Imported deck".
- `core/data/dao/ReviewDao.kt`: `DeckSummaryRow` + `deckSummaries(now)` (per-note title/total/due). `core/review/ReviewRepository.kt`: `Deck` model, `decks()`, `cardsForNote()`.
- `ReviewViewModel`: deck-aware `importAnki` (one holder note per Anki deck), `decks` StateFlow, `studyDeck(noteId)` (studies all of a deck's cards on demand).
- `ReviewScreen`: "Practise" top bar gains a **CloudDownload** action that opens `ankiweb.net/shared/decks` in the user's browser (Intent.ACTION_VIEW — no embedded web view) + the existing import action; a `DeckList` (counts, tap-to-study) shows under the session summary.

## CP24 — Note redaction for AI
- `core/notes/Redaction.kt` (NEW): markers `⟦…⟧`; `forAi` (→"[redacted]"), `stripMarkers` (preview), `toggle(text,start,end)` (wrap/unwrap a selection). +5 unit tests.
- `ui/screens/notes/NoteEditorScreen.kt`: body field is now a `TextFieldValue` (to read the selection); a **Redact** chip wraps/unwraps the selection; `EditorVisualTransformation` hides the markers (custom OffsetMapping) and greys the redacted span while still colouring `[[ ]]`/`!` links.
- Redactions stripped before any AI sees them: `NoteAiService.summarise/generateCards/findConnections/composeStream` use `Redaction.forAi(body)`; `HomeViewModel.buildPrompt` strips referenced-note bodies and previews use `stripMarkers`.

## CP25 — Smoothness polish
- The CP21 cooling/freeze is the main perceived-smoothness fix (no more constant graph recomposition on Home + Graph).
- `GraphScreen`: selected-node card now animates in/out (slide + fade) instead of popping.

Build: `:app:assembleDebug :app:testDebugUnitTest` green — 43/43 unit tests, APK 23.0 MB. Not run on a device this session.

## CP26 — AI Partner moved off the bottom bar
- `Destinations.kt`: removed `TopDestination.AiPartner` (back to 5 tabs); added `Routes.AI_PARTNER`.
- `MnemosNavHost.kt`: new full-screen `ai_partner` route; `MainPager` passes `onOpenAiPartner` → SettingsScreen.
- `SettingsScreen.kt`: an "AI Partner" button at the top opens it; `AiPartnerScreen` now has a back top bar.

## CP27 — Tab-switch + Home-swipe smoothness
- `HomeScreen.kt`: `pinchToExpand` rewritten with `awaitEachGesture` to react ONLY to 2-finger pinches and only consume during a real pinch — single-finger swipes now pass through to the pager (the home cards' old `detectTransformGestures` was eating one-finger pans, which made swiping off Home sluggish).
- `MnemosNavHost.kt`: bottom-bar taps + graph edge-tabs use `animateScrollToPage(tween(320, FastOutSlowIn))` for a crisp, consistent slide.

## CP28 — Practise session UX
- `ReviewViewModel.exitToDecks()` ends the current session → deck list/summary.
- `ReviewScreen`: top bar shows a Close icon during a session (exit to decks). Rating row: "Again" is now a compact `OutlinedIconButton` (Replay/rewind icon); Hard/Good/Easy are weighted buttons with tight `contentPadding` so labels no longer clip.

## CP29 — Live graph updates
- `GraphRepositoryImpl.ensureNodeForNote`: now updates an existing node's label when the note title changes (was create-only). Every note save already ensures the node, so added/edited notes reflect in the graph in real time via the existing Flow.

## CP30 — Editor `!` autocomplete + open-linked-note
- `WikiLinks.bareActiveQuery(text,cursor)` (in-progress `!…` at the cursor) and `WikiLinks.linkAt(text,offset,titles)` (resolve the link under the cursor).
- `NoteEditorViewModel.noteIdsByTitle` map; `NoteEditorScreen` gains `onOpenNote`.
- Body editor: typing `!` shows a note-suggestion menu that narrows as you type; clicking inserts `!Title `. When the cursor rests inside a resolved `[[link]]`/`!link`, an **Open "Title"** chip appears → saves + navigates to that note (a new editor entry). Saving forms the graph edge (existing bare-link sync). NOTE: tap-to-open is via the cursor-aware chip (not a raw tap on the glyph) to avoid fighting the text editor's own gesture handling.

Build: `:app:assembleDebug :app:testDebugUnitTest` green — 43/43 unit tests, APK 23.0 MB. Not run on a device this session.

## CP31 — Tab-switch optimization
- `MnemosNavHost.MainPager.animateTo`: only ADJACENT hops animate (quick 240ms slide); far jumps use `scrollToPage` (instant). This stops the pager composing every heavy intermediate screen mid-animation, which was the tab-switch lag.

## CP32 — Home prev/next note
- `HomeViewModel`: `_selectedIndex` folded into the state combine; `previousNote()/nextNote()` cycle the focused note (clamped). The focused note drives Now Reading, Now Playing, and the mini-graph centre.
- `HomeScreen.NowReadingCard`: ‹ › arrows in the header cycle the note (nested clickables so they don't trigger card-open).

## CP33 — Queue manager (tasks/alarms/events)
- Data: `QueueItemEntity` (+`queue_items` table, DB v4), `QueueDao`, `QueueRepository` (model `QueueItem`, enum `QueueKind` TASK/ALARM/EVENT) with add/remove/toggleDone/move(up|down by swapping positions).
- Alarms: `service/queue/QueueAlarmScheduler` (AlarmManager, exact→inexact fallback, guarded by canScheduleExactAlarms) + `QueueAlarmReceiver` (posts a high-priority notification; creates its own channel). Manifest: receiver + `SCHEDULE_EXACT_ALARM`.
- UI: `QueueScreen` (full-screen `Routes.QUEUE`) — list with done-checkbox, kind icon, time, up/down reorder, delete; FAB → add dialog (title + kind chips + TimePickerDialog for alarm/event). `QueueViewModel`.
- Home: a "Queue" button (Tune icon) in the Today's Queue header opens it; wired via `MainPager.onOpenQueue`.

## CP34 — Links always form a visible edge
- `GraphDao.nodeByLabel` + `GraphRepository.ensureConceptNode(label)` (find-or-create concept node).
- `WikiLinkSyncer.sync`: unresolved `[[ ]]`/`!` links now create an Obsidian-style "ghost" concept node and link to it, so EVERY link produces a visible connection (resolved links still bind to the note's node). Combined with CP29's live label sync + every-save node ensure, the graph reflects link changes after each save.

## CP35 — Scrollable note editor
- `NoteEditorScreen`: editor content column is now `verticalScroll`; body field uses `heightIn(min = 320.dp)` (no weight) so it grows with content and the whole page scrolls.

Build: `:app:assembleDebug :app:testDebugUnitTest` green — 43/43 unit tests, APK 23.2 MB. Not run on a device this session.

## CP36 — Pager recomposition fix (in-app tab lag)
- `MnemosNavHost`: the per-page depth transform now reads `pagerState.currentPageOffsetFraction` INSIDE the `graphicsLayer { }` block (draw phase) instead of in composition. Reading it in composition recomposed every visible page's whole subtree on every scroll frame — the real in-app tab-switch lag. Also softened the depth (scale 0.94→1, alpha 0.7→1) so less compositing.

## CP37 — Alarms ring + vibrate
- `QueueAlarmReceiver`: alarm channel now configured with the default **alarm ringtone** (USAGE_ALARM) + vibration pattern; the receiver also vibrates directly (VibratorManager/Vibrator) and plays the default alarm tone via RingtoneManager (audible even if notifications are quiet). Manifest: `VIBRATE` permission.

## CP38 — Practise add-card button moved
- `ReviewScreen`: removed the bottom FAB; "New card" is now a right-aligned `OutlinedButton` in a row directly under the top bar (below import/download, above the flashcard).

## CP39 — Daily roadmap (LLM)
- `HomeViewModel.generateRoadmap()` builds a prompt from today's due count + recent topics and calls `llm.generate`; `HomeState.roadmap/roadmapBusy`. `HomeScreen.DailyRoadmap` card with Generate/Regenerate + the result.

## CP40 — Graph "Load graph" button
- `GraphViewModel.loadFullGraph()` (injects `WikiLinkSyncer`) iterates all notes and runs `wikiLinks.sync` → ensures a node per note and rebuilds every `[[ ]]`/`!` edge. `GraphScreen`: an ExtendedFAB "Load graph" above the add-node FAB.

## CP41 — Obsidian import + images
- `core/notes/ImageStore` (filesDir/note_images, by filename) + `core/notes/ObsidianImporter` (SAF `OpenDocumentTree` → DocumentFile walk: each `.md` → a note, image files → ImageStore). Dep: `androidx.documentfile`. Coil was already present.
- `NotesViewModel.importObsidianVault(uri)` + message; `NotesScreen` top bar gains an "Import Obsidian vault" (DriveFolderUpload) action + snackbar.
- Editor image support: `NoteEditorViewModel.attachImage(uri)` (copies to store, appends `![[name]]`), `imageFile`, `isImageName`. `NoteEditorScreen`: an "Image" chip (picks image), and a horizontal **thumbnail strip** (Coil `AsyncImage`) of the note's `![[image]]` embeds. (Full inline markdown image rendering within the text flow is still a follow-up; images show as a thumbnail strip.)

Build: `:app:assembleDebug :app:testDebugUnitTest` green — 43/43 unit tests, APK 28.1 MB. Not run on a device this session.

## CP42 — More tab-swipe smoothness
- `MnemosNavHost` HorizontalPager: `beyondViewportPageCount = 1` so the neighbouring page is composed BEFORE the swipe (no first-composition mid-gesture). Dropped the per-page `alpha` from the depth transform (alpha<1 forces offscreen compositing each frame); kept a subtle scale-only (0.97→1) read in the draw phase.

Build: `:app:assembleDebug` green, APK 28.1 MB. Not run on a device this session.
