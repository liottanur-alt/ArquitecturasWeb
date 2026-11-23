import { test, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { getSelectedProfileId, selectProfile, clearSelectedProfile, safeReturn } from '../src/scripts/session.ts';

let storage;
let events;
beforeEach(() => {
  storage = new Map(); events = [];
  globalThis.sessionStorage = {
    getItem: key => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: key => storage.delete(key),
  };
  globalThis.window = { location: { origin: 'http://localhost:4321' }, dispatchEvent: event => events.push(event.type) };
});

test('guarda solo el ID, permite cambiarlo y quitarlo sin borrar otros datos', () => {
  storage.set('carrito', 'selección local');
  selectProfile(7);
  assert.equal(getSelectedProfileId(), 7);
  assert.equal(storage.get('ticketya-demo-profile-id'), '7');
  selectProfile(9);
  assert.equal(getSelectedProfileId(), 9);
  clearSelectedProfile();
  assert.equal(getSelectedProfileId(), null);
  assert.equal(storage.get('carrito'), 'selección local');
  assert.deepEqual(events, ['ticketya:profile', 'ticketya:profile', 'ticketya:profile']);
});

test('descarta JWT heredado sin convertirlo en perfil seleccionado', () => {
  storage.set('ticketya-session', JSON.stringify({ accessToken: 'legacy', email: 'viejo@example.com' }));
  assert.equal(getSelectedProfileId(), null);
  assert.equal(storage.has('ticketya-session'), false);
});

test('rechaza IDs inválidos y almacenamiento corrupto', () => {
  for (const value of [0, -1, 1.5, '7', null, Number.MAX_SAFE_INTEGER + 1]) {
    assert.throws(() => selectProfile(value));
    storage.set('ticketya-demo-profile-id', JSON.stringify(value));
    assert.equal(getSelectedProfileId(), null);
  }
  storage.set('ticketya-demo-profile-id', '{');
  assert.equal(getSelectedProfileId(), null);
});

test('no confirma selección cuando el almacenamiento no está disponible', () => {
  sessionStorage.setItem = () => { throw new Error('storage blocked'); };
  assert.throws(() => selectProfile(7));
  assert.deepEqual(events, []);
});

test('conserva destino y hash internos y rechaza destinos externos y bucles', () => {
  assert.equal(safeReturn('/perfil/#carrito'), '/perfil/#carrito');
  assert.equal(safeReturn('/eventos/1/?carrito=1'), '/eventos/1/?carrito=1');
  for (const value of ['https://example.com', '//example.com', '/\\example.com', '/login/?volver=/', '/registro/', null]) {
    assert.equal(safeReturn(value), '/');
  }
});
