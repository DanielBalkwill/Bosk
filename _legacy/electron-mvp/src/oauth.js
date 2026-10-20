'use strict';

// Google OAuth 2.0 for desktop/native apps using the loopback-redirect + PKCE flow.
// Reference: https://developers.google.com/identity/protocols/oauth2/native-app
//
// Why this flow: desktop apps can't keep a client secret confidential, so Google's
// recommended pattern is to open the *system browser*, redirect back to a localhost
// server we spin up on a random port, and protect the code exchange with PKCE.

const http = require('http');
const crypto = require('crypto');
const { shell } = require('electron');

const AUTH_ENDPOINT = 'https://accounts.google.com/o/oauth2/v2/auth';
const TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';
const USERINFO_ENDPOINT = 'https://openidconnect.googleapis.com/v1/userinfo';

function base64url(buf) {
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function makePkcePair() {
  const verifier = base64url(crypto.randomBytes(32));
  const challenge = base64url(crypto.createHash('sha256').update(verifier).digest());
  return { verifier, challenge };
}

// Minimal HTML shown in the browser tab after the redirect lands.
function resultPage(title, message) {
  return `<!doctype html><html><head><meta charset="utf-8"><title>${title}</title>
  <style>body{font-family:system-ui,sans-serif;background:#0b0d12;color:#e6e8ee;
  display:flex;align-items:center;justify-content:center;height:100vh;margin:0}
  .card{text-align:center;max-width:420px;padding:32px}h1{font-size:20px;margin:0 0 8px}
  p{color:#9aa0ad;line-height:1.5}</style></head>
  <body><div class="card"><h1>${title}</h1><p>${message}</p></div></body></html>`;
}

/**
 * Run the full interactive sign-in. Resolves with { tokens, profile }.
 * @param {{googleClientId:string, googleClientSecret:string, scopes:string[]}} config
 */
function signIn(config) {
  return new Promise((resolve, reject) => {
    const { verifier, challenge } = makePkcePair();
    const state = base64url(crypto.randomBytes(16));

    const server = http.createServer(async (req, res) => {
      try {
        const url = new URL(req.url, `http://127.0.0.1`);
        if (url.pathname !== '/callback') {
          res.writeHead(404).end();
          return;
        }

        const returnedState = url.searchParams.get('state');
        const code = url.searchParams.get('code');
        const error = url.searchParams.get('error');

        if (error) throw new Error(`Google returned an error: ${error}`);
        if (returnedState !== state) throw new Error('State mismatch — possible CSRF, aborting.');
        if (!code) throw new Error('No authorization code in callback.');

        const redirectUri = `http://127.0.0.1:${server.address().port}/callback`;
        const tokens = await exchangeCode(config, code, verifier, redirectUri);
        const profile = await fetchProfile(tokens.access_token);

        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end(resultPage('You\'re signed in', 'You can close this tab and return to the app.'));
        server.close();
        resolve({ tokens, profile });
      } catch (err) {
        res.writeHead(500, { 'Content-Type': 'text/html' });
        res.end(resultPage('Sign-in failed', String(err.message || err)));
        server.close();
        reject(err);
      }
    });

    server.on('error', reject);

    // Port 0 => OS assigns a free port. We only bind loopback so nothing external can reach it.
    server.listen(0, '127.0.0.1', () => {
      const redirectUri = `http://127.0.0.1:${server.address().port}/callback`;
      const authUrl = new URL(AUTH_ENDPOINT);
      authUrl.search = new URLSearchParams({
        client_id: config.googleClientId,
        redirect_uri: redirectUri,
        response_type: 'code',
        scope: (config.scopes || ['openid', 'email', 'profile']).join(' '),
        state,
        code_challenge: challenge,
        code_challenge_method: 'S256',
        access_type: 'offline',
        prompt: 'consent'
      }).toString();
      shell.openExternal(authUrl.toString());
    });

    // Safety valve: don't leave the loopback server hanging forever.
    setTimeout(() => {
      if (server.listening) {
        server.close();
        reject(new Error('Sign-in timed out after 5 minutes.'));
      }
    }, 5 * 60 * 1000);
  });
}

async function exchangeCode(config, code, verifier, redirectUri) {
  const body = new URLSearchParams({
    client_id: config.googleClientId,
    client_secret: config.googleClientSecret,
    code,
    code_verifier: verifier,
    grant_type: 'authorization_code',
    redirect_uri: redirectUri
  });
  const res = await fetch(TOKEN_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body
  });
  if (!res.ok) throw new Error(`Token exchange failed (${res.status}): ${await res.text()}`);
  const tokens = await res.json();
  tokens.obtained_at = Date.now();
  return tokens;
}

async function fetchProfile(accessToken) {
  const res = await fetch(USERINFO_ENDPOINT, {
    headers: { Authorization: `Bearer ${accessToken}` }
  });
  if (!res.ok) throw new Error(`Failed to fetch profile (${res.status}): ${await res.text()}`);
  return res.json();
}

module.exports = { signIn };
