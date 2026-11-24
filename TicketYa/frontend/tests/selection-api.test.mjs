import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import vm from 'node:vm';
import { mapCatalog } from '../src/scripts/catalog-data.ts';
import { parseAvailability, positiveId, saleOpen } from '../src/scripts/selection-data.ts';
import { ApiError } from '../src/scripts/api.ts';
const source = stripTypeScriptTypes(readFileSync(new URL('../src/scripts/selection-api.ts', import.meta.url), 'utf8').replace(/^import .*;\r?\n/gm, '').replace('export async function', 'async function'));
const detail = { id: 701, nombre: 'Real', estado: 'EN_CURSO', estadoPublicacion: 'PUBLICADO', fechaHora: '2099-01-01T21:00:00' };
const sector = { idEvento: 701, idSector: 55, tipo: 'CAMPO', precio: 100, disponibles: 3, cancelado: false, asientos: [] };
function setup(api) {
  const context = vm.createContext({ api, ApiError, mapCatalog, parseAvailability, positiveId, saleOpen });
  vm.runInContext(source, context); return context.selectionContext;
}
test('consulta detalle y disponibilidad del sector por Gateway sin cache ni mutaciones', async () => {
  const calls = [];
  const load = setup(async (path, options) => { calls.push({ path, options }); return { data: path.startsWith('/api/') ? detail : sector }; });
  const result = await load(701, 55);
  assert.equal(result.event.id, 701); assert.equal(result.sectors[0].idSector, 55); assert.equal(result.open, true);
  assert.deepEqual(calls.map(call => call.path), ['/api/eventos/701', '/eventos/701/sectores/55/disponibilidad']);
  assert.ok(calls.every(call => call.options.cache === 'no-store' && !call.options.method));
});

test('distingue un evento ausente de un evento real sin inventario', async () => {
  const missing = new ApiError(new Response(null, { status: 404 }), null);
  const withoutInventory = setup(async path => {
    if (path.startsWith('/api/')) return { data: detail };
    throw missing;
  });
  assert.equal((await withoutInventory(701)).sectors.length, 0);
  await assert.rejects(withoutInventory(701, 55), error => error === missing);
  const withoutEvent = setup(async path => {
    if (path.startsWith('/api/')) throw missing;
    throw new Error('Inventario caído');
  });
  await assert.rejects(withoutEvent(701), error => error === missing);
});
test('no recupera como válido un inventario de otro sector ni una consulta fallida', async () => {
  const load = setup(async path => ({ data: path.startsWith('/api/') ? detail : { ...sector, idSector: 99 } }));
  await assert.rejects(load(701, 55), /disponibilidad inválida/);
  const failed = setup(async () => { throw new Error('No se pudo consultar'); });
  await assert.rejects(failed(701, 55), /No se pudo consultar/);
});
