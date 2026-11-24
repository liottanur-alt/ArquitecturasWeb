import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import vm from 'node:vm';
import { parseHTML } from 'linkedom';

const source = stripTypeScriptTypes(readFileSync(new URL('../src/scripts/purchase-queue.ts', import.meta.url), 'utf8')
  .replace(/^import .*;\r?\n/gm, '').replace('export function', 'function'));
const settle = () => new Promise(resolve => setImmediate(resolve));
const waiting = { eventoId: 700, turnoId: 'turno', estado: 'EN_ESPERA', posicion: 2 };
const admitted = { ...waiting, estado: 'ADMITIDO', autorizacionCompra: 'autorizacion', venceEn: '2099-12-01T15:00:00Z' };

function setup(api) {
  let active = true;
  const { document } = parseHTML('<section><button>Entrar</button><p></p></section>');
  const panel = document.querySelector('section');
  const join = document.querySelector('button');
  const status = document.querySelector('p');
  const timers = new Map();
  const context = vm.createContext({
    api, AbortController, AbortSignal,
    setTimeout(callback, delay) { const id = Symbol(); timers.set(id, { callback, delay }); return id; },
    clearTimeout(id) { timers.delete(id); }
  });
  vm.runInContext(source, context);
  const queue = context.initQueue(700, panel, join, status, () => active, () => queue.refresh());
  queue.refresh();
  return {
    queue, panel, join, status, timers,
    leave() { active = false; queue.refresh(); },
    fireTimer() {
      const [id, timer] = timers.entries().next().value;
      timers.delete(id);
      timer.callback();
      return timer.delay;
    }
  };
}

test('muestra posición, recupera admisión y bloquea el turno al vencer', async () => {
  const calls = [];
  const view = setup(async (path, { method }) => {
    calls.push([path, method]);
    return { data: method === 'POST' ? waiting : { ...admitted, venceEn: new Date(Date.now() + 60000).toISOString() } };
  });
  view.join.click();
  await settle();
  assert.match(view.status.textContent, /Posición 2/);
  assert.equal(view.join.disabled, true);
  assert.equal(view.queue.authorization(), undefined);
  assert.equal(view.fireTimer(), 3000);
  await settle();
  assert.match(view.status.textContent, /Turno admitido/);
  assert.equal(view.queue.authorization(), 'autorizacion');
  view.fireTimer();
  assert.match(view.status.textContent, /venció/);
  assert.equal(view.queue.authorization(), undefined);
  assert.equal(view.join.disabled, false);
  assert.deepEqual(calls, [['/api/eventos/700/fila', 'POST'], ['/api/eventos/700/fila/mi-turno', 'GET']]);
});

test('al salir aborta la consulta y descarta una respuesta atrasada', async () => {
  let complete, signal;
  const view = setup(async (_path, options) => {
    signal = options.signal;
    return new Promise(resolve => { complete = resolve; });
  });
  view.join.click();
  view.leave();
  assert.equal(signal.aborted, true);
  complete({ data: waiting });
  await settle();
  assert.equal(view.panel.hidden, true);
  assert.equal(view.status.textContent, '');
  assert.equal(view.timers.size, 0);
});

test('un turno ya vencido no habilita la compra', async () => {
  const view = setup(async () => ({ data: { ...admitted, venceEn: '2020-01-01T00:00:00Z' } }));
  view.join.click();
  await settle();
  assert.match(view.status.textContent, /venció/);
  assert.equal(view.queue.authorization(), undefined);
  assert.equal(view.join.disabled, false);
  assert.equal(view.timers.size, 0);
});
