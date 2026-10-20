'use strict';

// Secure bridge: exposes a tiny, explicit API to the renderer. The renderer has no
// direct Node access (contextIsolation on, nodeIntegration off) — it can only call
// these whitelisted channels.

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('auth', {
  status: () => ipcRenderer.invoke('auth:status'),
  signIn: () => ipcRenderer.invoke('auth:signIn'),
  signOut: () => ipcRenderer.invoke('auth:signOut')
});
