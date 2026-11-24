import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import vm from 'node:vm';
import { parseHTML } from 'linkedom';

test('la bandeja del perfil filtra avisos y usa la lectura del servidor', async () => {
  const source = readFileSync(new URL('../src/scripts/notifications.ts', import.meta.url), 'utf8');
  const script = stripTypeScriptTypes(source.replace(/^import .*;\r?\n/gm, ''));
  const { document } = parseHTML('<div data-inbox><p class="inbox-status"></p><button class="inbox-read-all"></button><button class="inbox-retry"></button><button data-inbox-filter="unread"></button><button data-inbox-filter="read"></button><ol></ol></div>');
  const window = new EventTarget();
  window.setInterval = () => 0;
  const date = '2026-11-24T12:00:00Z';
  const notices = [
    { id: 1, idCliente: 7, asunto: 'Pendiente', mensaje: 'Aviso nuevo', fechaCreacion: date, fechaLectura: null },
    { id: 2, idCliente: 7, asunto: 'Revisado', mensaje: 'Aviso anterior', fechaCreacion: date, fechaLectura: date },
  ];
  const calls = [];
  const api = async (path, options) => {
    calls.push([path, options.method]);
    if (options.method === 'PATCH') notices[0].fechaLectura = date;
    return { data: { notificaciones: notices, noLeidas: notices.filter(n => n.fechaLectura === null).length } };
  };
  vm.runInNewContext(script, { document, window, Event, api, getSession: () => ({ accessToken: 'test', userId: 7 }) });
  const settle = () => new Promise(resolve => setImmediate(resolve));
  await settle();
  assert.equal(document.querySelectorAll('ol li').length, 2);
  document.querySelector('[data-inbox-filter="unread"]').click();
  assert.equal(document.querySelectorAll('ol li').length, 1);
  document.querySelector('ol button').click();
  await settle();
  assert.deepEqual(calls[1], ['/api/notificaciones/1/lectura', 'PATCH']);
  assert.equal(document.querySelectorAll('ol li').length, 0);
  document.querySelector('[data-inbox-filter="read"]').click();
  assert.equal(document.querySelectorAll('ol li').length, 2);
});
