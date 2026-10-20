# Security Policy

Mnemos is a **local-first, privacy-by-design** learning app. Security is a first-class
design goal, not an afterthought — this document explains the security model and how to
report a vulnerability.

## Reporting a vulnerability

**Please do not open a public issue for security vulnerabilities.**

- **Preferred:** use GitHub's private vulnerability reporting — the **Security** tab →
  **Report a vulnerability**. This keeps the report private until a fix is available.
- **Fallback:** email the maintainer at `danielbalkwilld@gmail.com` with the subject
  line `SECURITY: <short summary>`.

Please include:
- the affected version (`versionName`, e.g. `0.1.0-cp1`) and device/OS,
- a description of the issue and its impact,
- clear reproduction steps or a proof of concept,
- any suggested remediation.

**Response targets (best-effort, solo maintainer):**
- acknowledgement within **72 hours**,
- an initial assessment within **7 days**,
- coordinated disclosure once a fix ships. Credit is given to reporters who want it.

## Supported versions

Mnemos is pre-1.0 and under active development. Only the latest `main` / most recent
release receives security fixes until a stable release line is established.

| Version | Supported |
|---|---|
| latest `main` / newest release | ✅ |
| older pre-releases | ❌ |

## Security model (what Mnemos protects, and how)

### Assets
- **User notes and knowledge graph** — potentially sensitive personal content.
- **AI provider API keys** (BYOK) — the user's own credentials.
- **Optional Google Sign-In token** (only when the paid cloud-sync tier is used).

### Trust boundaries
1. **On device (trusted):** all notes, the graph, SRS state, and keys live locally in
   Room (SQLite) and `EncryptedSharedPreferences`. The app works fully offline.
2. **The AI provider the user chose (semi-trusted):** note content is sent to an LLM
   endpoint **only** when the user invokes an AI feature, and **only** to the provider
   whose key they supplied (Gemini, OpenAI, Anthropic, or an OpenAI-compatible endpoint
   such as Groq / OpenRouter). On-device **Gemini Nano** keeps even that on-device.
3. **Optional cloud sync (opt-in, paid tier):** never includes API keys.

### Controls
- **API keys never leave the device except to their own provider.** Keys are stored only
  in `EncryptedSharedPreferences` (`core/auth/SecureKeyStore.kt`) — AES-256-GCM for
  values, AES-256-SIV for key names, with the master key held in the **Android Keystore**.
  Keys are **excluded from cloud backup and device-to-device transfer**
  (`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`) and are **never
  logged**.
- **Data minimisation via redaction.** `⟦redaction⟧` markers (`core/notes/Redaction.kt`)
  strip marked content out of every outbound AI request, so the user controls exactly
  what reaches a third-party model.
- **No app-owned backend for the free tier.** Mnemos proxies nothing; there is no
  server that sees user data or keys (BYOK model → zero AI cost, zero custodial risk).
- **Least-privilege permissions.** The app requests only what its features need
  (`INTERNET`, foreground-service/media, overlay, alarms, notifications). See
  `AndroidManifest.xml`.
- **Release hardening.** Release builds enable R8/ProGuard minification and resource
  shrinking (`app/build.gradle.kts`, `app/proguard-rules.pro`).
- **No committed secrets.** The Google OAuth client id is read from the gitignored
  `local.properties` at build time; the Gemini/LLM key is runtime-only and in-app.

### Out of scope / known limitations
- A **compromised or rooted device** can read app-private storage; on-device encryption
  raises the bar but is not a defence against a privileged local attacker.
- **The chosen AI provider sees whatever note content is sent to it** (minus redactions).
  Users should pick a provider whose data policy they accept.
- Mnemos does not currently offer an app-level passcode/biometric lock on the note store
  (tracked for a future release).

> This policy will grow into a full threat model under `docs/` as the security review
> progresses.
