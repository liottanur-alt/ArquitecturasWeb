import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import vm from 'node:vm';
import { normalizeDraft } from '../src/scripts/selection-data.ts';
const source = stripTypeScriptTypes(readFileSync(new URL('../src/scripts/cart.ts', import.meta.url), 'utf8')
  .replace(/^import .*;\r?\n/gm, '').replace(/^export (?:type )?\{.*;\r?\n/gm, '').replace(/export /g, ''));
function setup() {
  let owner = 7;
  const storage = new Map();
  const context = vm.createContext({ normalizeDraft, getSelectedProfileId: () => owner, localStorage: {
    getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value),
  } });
  vm.runInContext(source, context);
  return { context, storage, setOwner: value => owner = value };
}
const draft = { eventId: 701, sectorId: 55, type: 'CAMPO', quantity: 2, seatIds: [], updatedAt: '2030-01-01T12:00:00Z' };
test('carrito separado por perfil y por evento/sector, sin precios guardados', () => {
  const { context, setOwner, storage } = setup();
  context.saveDraft(draft); context.saveDraft({ ...draft, sectorId: 56 });
  assert.equal(context.getCart().length, 2);
  context.saveDraft({ ...draft, quantity: 3 }); assert.equal(context.getCart().length, 2);
  setOwner(8); assert.equal(context.getCart().length, 0); context.saveDraft(draft);
  setOwner(7); assert.equal(context.getCart()[0].quantity, 3);
  context.removeDraft(701, 55); assert.equal(context.getCart().length, 1);
  assert.equal(JSON.parse(storage.get('ticketya-carrito-v2-perfil-7'))[0].sectorId, 56);
  assert.throws(() => context.saveDraft(draft, 8));
  setOwner(null); assert.throws(() => context.saveDraft(draft));
});
test('no convierte carrito de ejemplo ni afirma éxito ante almacenamiento inválido', () => {
  const { context, storage } = setup();
  storage.set('ticketya-carrito-usuario-7', JSON.stringify([{ eventId: 1, selection: [{ sectorId: 'campo' }] }]));
  assert.equal(context.getCart().length, 0);
  storage.set('ticketya-carrito-v2-perfil-7', '{');
  assert.throws(() => context.saveDraft(draft));
});
