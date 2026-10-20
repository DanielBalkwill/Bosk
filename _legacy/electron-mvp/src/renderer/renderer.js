'use strict';

const el = (id) => document.getElementById(id);
const show = (id) => el(id).classList.remove('hidden');
const hide = (id) => el(id).classList.add('hidden');

function render(status) {
  ['needs-setup', 'signed-out', 'signed-in'].forEach(hide);

  if (!status.configured) {
    show('needs-setup');
    return;
  }
  if (status.profile) {
    el('name').textContent = status.profile.name || status.profile.email || 'Signed in';
    el('email').textContent = status.profile.email || '';
    if (status.profile.picture) el('avatar').src = status.profile.picture;
    show('signed-in');
    return;
  }
  show('signed-out');
}

async function refresh() {
  render(await window.auth.status());
}

el('sign-in').addEventListener('click', async () => {
  const btn = el('sign-in');
  const err = el('error');
  err.classList.add('hidden');
  btn.disabled = true;
  btn.textContent = 'Waiting for browser…';
  try {
    await window.auth.signIn();
    await refresh();
  } catch (e) {
    err.textContent = String(e.message || e);
    err.classList.remove('hidden');
    btn.disabled = false;
    btn.innerHTML = '<span class="g">G</span> Sign in with Google';
  }
});

el('sign-out').addEventListener('click', async () => {
  await window.auth.signOut();
  await refresh();
});

refresh();
