# Contributing to Mnemos

Thanks for your interest in Mnemos. This is an early-stage, actively developed project —
contributions, bug reports, and ideas are welcome.

## Ground rules

- **Be respectful.** Assume good faith; keep discussion constructive.
- **Security issues go through [`SECURITY.md`](SECURITY.md)**, never a public issue.
- **Open an issue before a large change.** For anything beyond a small fix, please file
  an issue first so we can agree on the approach before you invest time.

## Licensing of contributions

Mnemos is licensed under **AGPL-3.0** (see [`LICENSE`](LICENSE)). By submitting a
contribution you agree that it is licensed under the same terms (inbound = outbound).
Please only submit code you have the right to contribute.

## Development setup

**Requirements:** Android Studio (Ladybug or newer) or a command-line Android SDK, and
**JDK 17**.

```bash
cp local.properties.example local.properties   # set sdk.dir (Android Studio writes this)
./gradlew testDebugUnitTest                     # run the ~120-test JVM unit suite
./gradlew assembleDebug                         # build the debug APK
./gradlew installDebug                          # install on a device/emulator
```

No API keys live in config — AI keys are entered **in-app** (BYOK, encrypted on-device).
Optional Google Sign-In needs `GOOGLE_SERVER_CLIENT_ID` in `local.properties`
(see [`.env.example`](.env.example)).

## Architecture & conventions

- **Read [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) first** — it records the *why*
  behind every major structural decision. Don't fight it without discussion.
- **Keep `core/` pure.** `core/` holds logic and data with no Jetpack Compose
  dependency, so it stays unit-testable. UI lives in `ui/`, background work in `service/`.
- **No blocking the main thread.** Every AI/network/DB call is a `suspend` function or
  `Flow`; the UI observes `StateFlow` from ViewModels.
- **No hardcoded colours or dimensions** in screens — use the tokens in `ui/theme/`.
- **No secrets in source**, ever. See [`SECURITY.md`](SECURITY.md).
- Follow the existing Kotlin style (the surrounding code is the style guide); match the
  naming and comment density already in the file you're editing.

## Pull requests

1. Fork and branch from `main` (e.g. `fix/editor-caret`, `feat/anki-import`).
2. Keep PRs focused — one logical change per PR.
3. **Add or update tests** for logic changes, and make sure the suite passes:
   `./gradlew testDebugUnitTest`.
4. CI (GitHub Actions) must be green — it compiles the app and runs the unit tests on
   every push and PR.
5. Write a clear PR description: what changed, why, and how you tested it.

## Reporting bugs

Open an issue with: what you expected, what happened, your device/OS and app version,
and steps to reproduce. Logs or screenshots help a lot.
