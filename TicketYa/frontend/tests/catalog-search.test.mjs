import { test } from 'node:test';
import assert from 'node:assert/strict';
import { searchPath, matchesText, latestRequest, eventTypes } from '../src/scripts/catalog-search.ts';

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

test('texto se aplica localmente sobre nombre, artista o lugar', () => {
  const event = { title: 'Música en vivo', artist: 'José', venue: 'Teatro Central' };
  for (const query of [' MUSICA ', 'jose', 'central', '']) assert.equal(matchesText(event, query), true);
  assert.equal(matchesText(event, 'otro'), false);
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
