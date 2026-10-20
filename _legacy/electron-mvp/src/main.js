'use strict';

const { app, BrowserWindow, ipcMain } = require('electron');
const fs = require('fs');
const path = require('path');
const oauth = require('./oauth');
const store = require('./store');

let mainWindow = null;

function loadConfig() {
  const configPath = path.join(__dirname, '..', 'config.json');
  if (!fs.existsSync(configPath)) {
    return null; // renderer will show a setup-needed state
  }
  try {
    return JSON.parse(fs.readFileSync(configPath, 'utf8'));
  } catch (err) {
    throw new Error(`config.json is present but invalid JSON: ${err.message}`);
  }
}

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 480,
    height: 640,
    resizable: false,
    backgroundColor: '#0b0d12',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  mainWindow.removeMenu();
  mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'));
}

// ---- IPC: the renderer can only do these four things, nothing else ----

ipcMain.handle('auth:status', () => {
  const config = loadConfig();
  const session = store.load();
  return {
    configured: Boolean(config && config.googleClientId && !config.googleClientId.startsWith('YOUR_')),
    profile: session ? session.profile : null
  };
});

ipcMain.handle('auth:signIn', async () => {
  const config = loadConfig();
  if (!config) throw new Error('No config.json found. Copy config.example.json and add your Google OAuth credentials.');
  const { tokens, profile } = await oauth.signIn(config);
  store.save({ tokens, profile, savedAt: Date.now() });
  return { profile };
});

ipcMain.handle('auth:signOut', () => {
  store.clear();
  return { ok: true };
});

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});
