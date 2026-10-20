# Ambient Companion — Desktop Shell (MVP step 1)

A background AI summarisation & replay companion. This first build does exactly one
thing: **sign in with Google and show your identity.** Everything else (Gemini BYOK,
TTS playback queue, command popup) builds on top of this shell.

## Stack
- **Electron** desktop shell (PC first)
- **Google OAuth 2.0** — desktop loopback + PKCE flow, system-browser based
- **Tokens stored encrypted on-device** via Electron `safeStorage` (OS keychain)
- No backend, no AI cost to you — pure local identity for now

## One-time Google setup (~2 minutes)
1. Go to <https://console.cloud.google.com/> → create a project (any name).
2. **APIs & Services → OAuth consent screen** → choose *External*, fill the basics,
   add yourself as a *Test user*.
3. **APIs & Services → Credentials → Create credentials → OAuth client ID**.
4. Application type: **Desktop app**. Create it.
5. Copy the **Client ID** and **Client secret**.
6. In this folder: copy `config.example.json` → `config.json` and paste them in.

> `config.json` is gitignored so your credentials are never committed.

## Run
```bash
npm install
npm start
```

Click **Sign in with Google** → your browser opens → approve → the tab confirms and
the app shows your name, email and avatar. Tokens persist (encrypted) across restarts;
**Sign out** clears them.

## Project layout
```
src/
  main.js              Electron main process + IPC handlers
  oauth.js             Google loopback + PKCE sign-in flow
  store.js             Encrypted on-device session store (safeStorage)
  preload.js           Locked-down bridge exposed to the UI
  renderer/            The window UI (sign-in / signed-in states)
config.example.json    Template — copy to config.json with your creds
```

## What's next (not in this build)
- Gemini BYOK: paste your AI Studio key, calls go client → Gemini directly
- TTS playback queue (Edge TTS) with interruptible reordering
- The "Authenticator-style" command popup with inline AI
