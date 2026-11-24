import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { getAuthenticatedUserId, getSession, startSession, clearSession, safeReturn } from '../src/scripts/session.ts';

let storage;
beforeEach(() => {
  storage = new Map();
  globalThis.sessionStorage = {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: key => storage.delete(key),
  };
  globalThis.window = new EventTarget();
  window.location = { origin: 'https://ticketya.example' };
});

function token(sub = '7', exp = Math.floor(Date.now() / 1000) + 120) {
  return `e30.${Buffer.from(JSON.stringify({ sub, exp, roles: ['CLIENTE'] })).toString('base64url')}.fixture`;
}

test('inicia y cierra la sesión y avisa a los consumidores', () => {
  let changes = 0;
  window.addEventListener('ticketya:profile', () => changes++);
  const accessToken = token();
  assert.deepEqual(startSession({ accessToken }).roles, ['CLIENTE']);
  assert.equal(getSession().accessToken, accessToken);
  assert.equal(getAuthenticatedUserId(), 7);
  clearSession();
  assert.equal(getSession(), null);
  assert.equal(changes, 2);
});

test('descarta los perfiles demo y los tokens vencidos, inválidos o con ID impreciso', () => {
  storage.set('ticketya-demo-profile-id', '7');
  assert.equal(getSession(), null);
  for (const accessToken of ['inválido', token('7', 1), token('9007199254740993')]) {
    assert.throws(() => startSession({ accessToken }));
    storage.set('ticketya-auth-session', accessToken);
    assert.equal(getSession(), null);
    assert.equal(storage.has('ticketya-auth-session'), false);
  }
});

test('limita el retorno a rutas locales fuera de login y registro', () => {
  assert.equal(safeReturn('/perfil/?x=1#compras'), '/perfil/?x=1#compras');
  for (const path of ['//evil.example', '/\\evil.example', '/login/?volver=/', '/registro/']) {
    assert.equal(safeReturn(path), '/');
  }
});
