# Mnemos — Architecture

This document records the *why* behind every major decision. Read it before changing
structural things.

## Platform: Native Android (Kotlin + Jetpack Compose)

**Decision:** Native Android, not React Native/Expon.

**Reasoning:** The flagship features — the **overlay popup** (`SYSTEM_ALERT_WINDOW`),
the **foreground ambient daemon**, **notification media controls**, and **TextToSpeech**
— are all deeply native Android APIs. Fighting the React Native bridge for always-on
overlay + background work is exactly where cross-platform stacks bleed time and
reliability. Native also gives first-class **Material You** dynamic colour, which the
design direction depends on. Cost: an iOS port is a future rewrite of the UI layer, but
the `/core` logic (FSRS, Gemini client, profile manager) is plain Kotlin and portable
via KMP later if desired.

## Module structure: feature-grouped `core` / `ui` / `service`

`core/` holds pure logic and data (no Compose). `ui/` is Compose + ViewModels. `service/`
is the background/overlay/notification layer. This keeps the testable brain (`core`)
independent of Android UI, and isolates the permission-heavy background code.

## Dependency injection: Hilt

Standard for native Android, compile-time validated, integrates with WorkManager and
ViewModels. The object graph is rooted in `MnemosApplication`. CP1 needs almost none of
it (`SecureKeyStore` self-provides via `@Inject`); it pays off from CP2 onward when DB,
network, and repositories need wiring.

## Async: Coroutines + Flow (global rule #4)

Every Gemini call and DB query is a `suspend` function or `Flow`. The UI observes state
via `StateFlow` in ViewModels. **No AI call ever blocks the main thread.** Streaming
Gemini output is a `Flow<String>` so the TTS engine can speak before generation finishes.

## AI: BYOK Gemini, key encrypted on-device (global rule #5)

Users bring their own Gemini API key. We bear **zero AI cost** and never proxy their
data. The key lives only in `EncryptedSharedPreferences` (`core/auth/SecureKeyStore`),
AES-256 with key material in the Android Keystore, and is excluded from backup/transfer
(`res/xml/*_rules.xml`, file name `mnemos_secure_prefs`). It is only ever sent to
`generativelanguage.googleapis.com`. The **Knowledge Profile** ("Mulch pattern") is
prepended to each call so the model has context cheaply without re-ingesting the corpus.

## Spaced repetition: FSRS-4.5, as a pure function (global rule #6)

`core/srs/Fsrs.kt` exposes `fsrsSchedule(card, rating, now) -> card` with **no side
effects**, making the scheduler fully unit-testable independent of Room/UI. FSRS gives
materially better retention-per-review than SM-2.

## The graph is the curriculum (global rule #7)

`NodeEntity`/`EdgeEntity` in Room are the single source of truth. The `CurriculumEngine`
(CP5) reads the graph + SRS state to decide what the ambient daemon surfaces next — there
is no separate hand-maintained queue that could drift from the graph.

## Design system: Material You first, "deep ink on paper" (global rules #8, #9)

Dark-mode-primary, warm off-white text on near-black, a single warm-gold accent that
**Material You dynamic colour** overrides from the user's wallpaper on Android 12+.
Typographic hierarchy uses a characterful serif for titles and a clean sans for body
(system fallbacks in CP1; DM Serif Display / DM Sans via downloadable fonts in CP3).
Tokens live in `ui/theme/` — colours/dimensions are never hardcoded in screens.
Elevation uses colour tinting, not drop shadows.

## Data: Room local-first, Supabase sync only on paid tier

Everything works fully offline and local. Cloud sync (`core/sync`) is a paid-tier add-on
and never includes the API key. This keeps the free tier private-by-default and cheap to
run (no per-user server cost).

## Versions (pinned in `gradle/libs.versions.toml`)

AGP 8.7.3 · Kotlin 2.0.21 (Compose compiler plugin) · Compose BOM 2024.12.01 ·
Room 2.6.1 · WorkManager 2.10 · Hilt 2.52 · minSdk 26 / target 35 · JDK 17.
A Gradle **version catalog** is the single source of dependency truth.

## Build secrets

`GOOGLE_SERVER_CLIENT_ID` (Web OAuth client id for Sign-In) is read from
`local.properties` into `BuildConfig` at build time — never committed. The Gemini key is
runtime-only (see above).
