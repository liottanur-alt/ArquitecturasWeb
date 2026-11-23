import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import vm from 'node:vm';
import * as helpers from '../src/scripts/profile-data.ts';
import { ApiError, ApiConnectionError } from '../src/scripts/api.ts';

const source = stripTypeScriptTypes(readFileSync(new URL('../src/scripts/register.ts', import.meta.url), 'utf8').replace(/^import .*;\r?\n/gm, ''));
const settle = () => new Promise(resolve => setImmediate(resolve));
function setup(api) {
  const elements = new Map();
  const get = id => {
    if (!elements.has(id)) elements.set(id, {
      value: '', hidden: false, disabled: false, textContent: '', handlers: {},
      addEventListener(name, handler) { this.handlers[name] = handler; },
      setAttribute() {}, removeAttribute() {}, setCustomValidity() {}, focus() {}, reportValidity() {},
      classList: { add() {}, remove() {} },
    });
    return elements.get(id);
  };
  vm.runInNewContext(source, {
    ...helpers, api, ApiError, ApiConnectionError, URLSearchParams,
    document: { querySelector: selector => get(selector.slice(1)), getElementById: get },
    location: { search: '' }, safeReturn: value => value,
    matchMedia: () => ({ matches: true }),
  });
  return { get, submit: () => get('register-form').handlers.submit({ preventDefault() {} }) };
}
function fill(get) {
  for (const [name, value] of Object.entries({ nombre: ' Ana ', email: 'ana@example.com', dni: 'AB-123', ciudad: 'Tandil' })) get(name).value = value;
}
test('espera CLIENTE, envía POST sin contraseña y evita repetir un alta confirmada', async () => {
  const requests = [];
  const { get, submit } = setup(async (path, options) => {
    requests.push({ path, options });
    if (path === '/roles') return { data: [{ id: 71, name: 'CLIENTE' }] };
    return { data: { id: 32, ...options.body } };
  });
  assert.equal(get('register-fields').disabled, true);
  await settle(); fill(get);
  await submit(); await submit();
  assert.equal(requests.length, 2);
  assert.equal(requests[1].path, '/usuarios');
  assert.equal(requests[1].options.method, 'POST');
  assert.equal(requests[1].options.body.idRol, 71);
  assert.equal('password' in requests[1].options.body, false);
  assert.equal(get('register-success').hidden, false);
  assert.equal(get('register-fields').disabled, true);
});
test('bloquea el alta sin CLIENTE y ofrece reintentar', async () => {
  let calls = 0;
  const { get, submit } = setup(async () => { calls++; return { data: [] }; });
  await settle(); fill(get); await submit();
  assert.equal(calls, 1);
  assert.equal(get('register-fields').disabled, true);
  assert.equal(get('retry-roles').hidden, false);
});
test('409 conserva los datos y permite corregir sin confirmar éxito', async () => {
  const { get, submit } = setup(async path => {
    if (path === '/roles') return { data: [{ id: 55, name: 'CLIENTE' }] };
    throw new ApiError(new Response(null, { status: 409 }), null);
  });
  await settle(); fill(get); await submit();
  assert.equal(get('nombre').value, ' Ana ');
  assert.equal(get('register-fields').disabled, false);
  assert.equal(get('register-error').hidden, false);
  assert.match(get('register-error-text').textContent, /email o DNI/);
});
