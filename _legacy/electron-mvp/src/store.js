'use strict';

// Tiny on-device session store. Tokens are encrypted with Electron's safeStorage,
// which uses the OS keychain (Keychain on macOS, libsecret on Linux, DPAPI on Windows).
// Nothing here ever leaves the machine — this is the BYOK "your keys stay local" promise.

const fs = require('fs');
const path = require('path');
const { app, safeStorage } = require('electron');

function sessionPath() {
  return path.join(app.getPath('userData'), 'session.bin');
}

function save(session) {
  const json = JSON.stringify(session);
  const data = safeStorage.isEncryptionAvailable()
    ? safeStorage.encryptString(json)
    : Buffer.from(json, 'utf8'); // fallback if no OS keychain is present
  fs.writeFileSync(sessionPath(), data);
}

function load() {
  try {
    const data = fs.readFileSync(sessionPath());
    const json = safeStorage.isEncryptionAvailable()
      ? safeStorage.decryptString(data)
      : data.toString('utf8');
    return JSON.parse(json);
  } catch {
    return null; // no session yet, or unreadable
  }
}

function clear() {
  try {
    fs.unlinkSync(sessionPath());
  } catch {
    /* already gone */
  }
}

module.exports = { save, load, clear };
