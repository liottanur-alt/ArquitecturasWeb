import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import vm from 'node:vm';
import { parseHTML } from 'linkedom';
import { parseEntry } from '../src/scripts/entry-data.ts';
import { parseRefunds } from '../src/scripts/refund-data.ts';

test('una resolución incierta se reconcilia con solicitud, entrada y pendientes del Gateway', async () => {
  const source = readFileSync(new URL('../src/scripts/refund-admin.ts', import.meta.url), 'utf8');
  const script = stripTypeScriptTypes(source.replace(/^import .*;\r?\n/gm, ''));
  const page = readFileSync(new URL('../src/pages/administracion/index.astro', import.meta.url), 'utf8');
  const { document, window } = parseHTML(page.match(/<OperationalLayout[^>]*>([\s\S]*?)<\/OperationalLayout>/)[1]);
  const confirmation = document.querySelector('#admin-confirm');
  confirmation.showModal = () => { confirmation.open = true; };
  const choose = document.querySelector('#refund-admin-decision');
  Object.defineProperty(choose, 'value', { value: '', writable: true });

  let session = { userId: 1, accessToken: 'cliente', roles: ['CLIENTE'] };
  let state = 'PENDIENTE';
  const calls = [];
  const request = () => ({
    id: 15, idEntrada: 3, idCompra: 7, idCliente: 8,
    estadoSolicitud: state, descripcion: 'Prueba de devolución',
    fechaSolicitud: '2026-11-01T10:00:00Z',
  });
  const fakeApi = async (path, options = {}) => {
    calls.push([path, options.method ?? 'GET']);
    if (path === '/solicitudes/pendientes') return { data: state === 'PENDIENTE' ? [request()] : [] };
    if (path === '/solicitudes/15') return { data: request() };
    if (path === '/entradas/3') {
      return {
        data: {
          id: 3, qr: 'qr-prueba', estadoQr: 'NO_ESCANEADO', idCliente: 8, idEvento: 99, idSector: 21,
          idAsiento: null, precio: 1500,
          estadoEntrada: state === 'PENDIENTE' ? 'SOLICITUD_DEVOLUCION' : 'DEVOLUCION_ACEPTADA',
        },
      };
    }
    assert.equal(path, '/solicitudes/15/resolver');
    assert.equal(options.body.idAdministrador, 2);
    assert.equal(options.body.estadoSolicitud, 'ACEPTADA');
    state = 'ACEPTADA';
    throw new Error('Se perdió la respuesta');
  };
  const context = vm.createContext({ document, window, Error, parseEntry, parseRefunds, api: fakeApi, getSession: () => session });
  vm.runInContext(script, context);
  assert.deepEqual(calls, []);
  assert.ok(document.querySelector('#refund-admin').hidden);

  session = { userId: 2, accessToken: 'admin', roles: ['ADMINISTRADOR'] };
  window.dispatchEvent(new window.Event('ticketya:profile'));
  const settle = () => new Promise(resolve => setImmediate(resolve));
  await settle();
  document.querySelector('#refund-admin-list button').click();
  await settle();
  assert.match(document.querySelector('#refund-admin-detail').textContent, /SOLICITUD_DEVOLUCION/);
  choose.value = 'ACEPTADA';
  const form = document.querySelector('#refund-admin-resolution');
  form.requestSubmit = () => form.dispatchEvent(new window.Event('submit', { cancelable: true }));
  form.dispatchEvent(new window.Event('submit', { cancelable: true }));
  assert.equal(confirmation.open, true);
  assert.equal(calls.filter(([path]) => path === '/solicitudes/15/resolver').length, 0);
  confirmation.returnValue = 'confirm';
  confirmation.open = false;
  confirmation.dispatchEvent(new window.Event('close'));
  await settle();

  assert.equal(calls.filter(([path]) => path === '/solicitudes/15/resolver').length, 1);
  assert.match(document.querySelector('#refund-admin-result').textContent, /Se perdió la respuesta/);
  assert.match(document.querySelector('#refund-admin-detail').textContent, /#15 · ACEPTADA/);
  assert.match(document.querySelector('#refund-admin-detail').textContent, /DEVOLUCION_ACEPTADA/);
  assert.ok(form.hidden);
  assert.match(document.querySelector('#refund-admin-list-status').textContent, /No hay solicitudes pendientes/);
});
