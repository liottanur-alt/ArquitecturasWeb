import { test } from 'node:test';
import assert from 'node:assert/strict';
import { searchPath, latestRequest, eventTypes, catalogWindow } from '../src/scripts/catalog-search.ts';

test('Hoy y fin de semana siguen el calendario de Buenos Aires', () => {
  const instant = new Date('2026-11-28T01:00:00Z');
  assert.deepEqual(catalogWindow('today', instant), { from: '2026-11-27T22:00:00', to: '2026-11-27T23:59:59.999999' });
  const weekend = catalogWindow('weekend', instant);
  assert.deepEqual(weekend, { from: '2026-11-28T00:00:00', to: '2026-11-29T23:59:59.999999' });
  const url = new URL(searchPath('', '', 0, '', weekend), 'http://localhost');
  assert.equal(url.searchParams.get('fechaDesde'), weekend.from);
  assert.equal(url.searchParams.get('fechaHasta'), weekend.to);
});

test('construye solo filtros soportados, enum, orden y página de base cero', () => {
  for (const tipo of eventTypes) {
    const url = new URL(searchPath(tipo, ' Mar del Plata ', 2), 'http://localhost');
    assert.equal(url.pathname, '/api/eventos/buscar');
    assert.deepEqual(Object.fromEntries(url.searchParams), { pagina: '2', tamanio: '10', ordenar: 'fechaHora,asc', tipo, ciudad: 'Mar del Plata' });
  }
  const all = new URL(searchPath('', '', 0), 'http://localhost');
  assert.equal(all.searchParams.has('tipo'), false);
  assert.equal(all.searchParams.has('ciudad'), false);
  for (const page of [-1, 1.5, NaN]) assert.throws(() => searchPath('', '', page));
  assert.throws(() => searchPath('Música', '', 0));
});

test('envía el texto al servidor y ordena por relevancia antes de paginar', () => {
  const url = new URL(searchPath('RECITAL', 'Tandil', 1, '  Música + José  '), 'http://localhost');
  assert.equal(url.searchParams.get('q'), 'Música + José');
  assert.equal(url.searchParams.get('ordenar'), 'relevancia,desc');
  assert.equal(url.searchParams.get('pagina'), '1');
});

test('descarta respuesta anterior aunque el transporte ignore la cancelación', async () => {
  const run = latestRequest();
  let firstResolve, secondResolve, firstSignal;
  const first = run(signal => { firstSignal = signal; return new Promise(resolve => { firstResolve = resolve; }); });
  const second = run(() => new Promise(resolve => { secondResolve = resolve; }));
  assert.equal(firstSignal.aborted, true);
  secondResolve('nuevo');
  assert.deepEqual(await second, { current: true, data: 'nuevo' });
  firstResolve('viejo');
  assert.deepEqual(await first, { current: false });
});

test('errores anteriores no reemplazan éxito actual; errores actuales se informan', async () => {
  const run = latestRequest();
  let rejectOld;
  const old = run(() => new Promise((_, reject) => { rejectOld = reject; }));
  assert.deepEqual(await run(async () => ['actual']), { current: true, data: ['actual'] });
  rejectOld(new Error('error viejo'));
  assert.deepEqual(await old, { current: false });
  await assert.rejects(run(async () => { throw new Error('error actual'); }), /error actual/);
});
