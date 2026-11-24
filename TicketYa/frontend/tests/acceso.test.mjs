import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import vm from 'node:vm';
import { parseHTML } from 'linkedom';
import { ApiError } from '../src/scripts/api.ts';
import { accessDenial, accessRequest, parseAccessEntry } from '../src/scripts/acceso-data.ts';

const qr = '54b24ff7-6648-422f-b7e7-7515f352046e';
const ticket = { id: 40, idEvento: 7, idSector: 3, qr, estadoEntrada: 'PAGA', estadoQr: 'NO_ESCANEADO' };
const path = '/entradas/qr/' + qr;
const source = stripTypeScriptTypes(readFileSync(new URL('../src/scripts/acceso.ts', import.meta.url), 'utf8')
  .replace(/^import .*;\r?\n/gm, ''));
const settle = () => new Promise(resolve => setImmediate(resolve));

function setup(api, session = { accessToken: 'actual', roles: ['PERSONAL_ACCESO'] }) {
  const { document, window } = parseHTML(`<html><body>
    <section id="access-panel" hidden>
      <form id="access-form"><input id="access-event"><input id="access-qr">
        <button id="access-lookup"></button><button id="access-confirm" disabled></button><button id="access-next"></button>
      </form>
      <p id="access-status"></p><p id="access-entry" hidden></p>
    </section><p id="access-permission"></p>
  </body></html>`);
  document.querySelector('#access-event').valueAsNumber = 7;
  document.querySelector('#access-qr').value = qr;
  let redirect;
  vm.runInNewContext(source, {
    document, window, api, ApiError, Error, accessDenial, accessRequest, parseAccessEntry,
    getSession: () => session, location: { replace: value => { redirect = value; } }
  });
  return {
    confirm: document.querySelector('#access-confirm'),
    panel: document.querySelector('#access-panel'),
    status: document.querySelector('#access-status'),
    submit: () => document.querySelector('#access-form').dispatchEvent(new window.Event('submit', { cancelable: true })),
    get redirect() { return redirect; }
  };
}

test('consulta el código y exige confirmar antes de registrar el ingreso', async () => {
  const calls = [];
  const view = setup(async (requestPath, options) => {
    calls.push([requestPath, options.method ?? 'GET']);
    return { data: options.method === 'PATCH' ? { ...ticket, estadoQr: 'ESCANEADO' } : ticket };
  });
  view.submit();
  await settle();
  assert.equal(view.confirm.disabled, false);
  assert.deepEqual(calls, [[path, 'GET']]);
  view.confirm.click();
  await settle();
  assert.equal(view.confirm.disabled, true);
  assert.equal(view.status.textContent, 'Ingreso registrado.');
  assert.deepEqual(calls, [[path, 'GET'], [path + '/uso?idEvento=7', 'PATCH']]);
});

test('otro evento, QR usado y entrada no vigente bloquean la confirmación', async () => {
  for (const [entry, expected] of [
    [{ ...ticket, idEvento: 8 }, /otro evento/],
    [{ ...ticket, estadoQr: 'ESCANEADO' }, /ya fue registrado/],
    [{ ...ticket, estadoEntrada: 'SOLICITUD_DEVOLUCION' }, /devolución pendiente/]
  ]) {
    const view = setup(async (_path, options) => {
      assert.equal(options.method, undefined);
      return { data: entry };
    });
    view.submit();
    await settle();
    assert.equal(view.confirm.disabled, true);
    assert.match(view.status.textContent, expected);
  }
});

test('un conflicto de ingreso conserva el mensaje del servidor y deshabilita repetir el PATCH', async () => {
  let marks = 0;
  const view = setup(async (_path, options) => {
    if (options.method !== 'PATCH') return { data: ticket };
    marks++;
    throw new ApiError(new Response(null, { status: 409 }), { error: 'La entrada ya fue utilizada' });
  });
  view.submit();
  await settle();
  view.confirm.click();
  await settle();
  assert.match(view.status.textContent, /ya fue utilizada/);
  assert.equal(view.confirm.disabled, true);
  view.confirm.click();
  assert.equal(marks, 1);
});

test('requiere rol operativo y pide sesión con retorno a acceso', async () => {
  const customer = setup(async () => assert.fail('No debe consultar'), { roles: ['CLIENTE'] });
  assert.equal(customer.panel.hidden, true);
  assert.equal(customer.redirect, undefined);
  const anonymous = setup(async () => assert.fail('No debe consultar'), null);
  assert.equal(anonymous.redirect, '/login/?volver=%2Facceso%2F');
});
