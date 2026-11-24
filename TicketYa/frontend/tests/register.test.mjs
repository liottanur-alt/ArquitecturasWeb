import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import vm from 'node:vm';
import * as helpers from '../src/scripts/profile-data.ts';
import { ApiError, ApiConnectionError } from '../src/scripts/api.ts';

const source = stripTypeScriptTypes(readFileSync(new URL('../src/scripts/register.ts', import.meta.url), 'utf8').replace(/^import .*;\r?\n/gm, ''));

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
  const location = { search: "?volver=%2Feventos%2F%3Fid%3D7", assign(value) { this.target = value; } };
  vm.runInNewContext(source, {
    ...helpers, api, ApiError, ApiConnectionError, URLSearchParams, TextEncoder,
    document: { querySelector: selector => get(selector.slice(1)), getElementById: get },
    location, safeReturn: value => value,
    matchMedia: () => ({ matches: true }),
  });
  return { get, location, submit: () => get('register-form').handlers.submit({ preventDefault() {} }) };
}
function fill(get) {
  for (const [name, value] of Object.entries({ nombre: ' Ana ', email: 'ana@example.com', dni: 'AB-123', ciudad: 'Tandil', password: 'secreto' })) get(name).value = value;
}
test('registra una cuenta con contraseña y conserva el retorno al dirigir al login', async () => {
  const requests = [];
  const { get, submit, location } = setup(async (path, options) => {
    requests.push({ path, options });
    return { data: { id: 32, idRol: 71, ...options.body } };
  });
  fill(get);
  await submit(); await submit();
  assert.equal(requests.length, 1);
  assert.equal(requests[0].path, '/auth/registro');
  assert.equal(requests[0].options.method, 'POST');
  assert.equal(requests[0].options.body.nombre, 'Ana');
  assert.equal(requests[0].options.body.password, 'secreto');
  assert.equal('idRol' in requests[0].options.body, false);
  assert.equal(location.target, '/login/?volver=%2Feventos%2F%3Fid%3D7');
  assert.equal(get('password').value, '');
});

test('valida el límite de contraseña en bytes antes de enviar', async () => {
  const { get, submit } = setup(async () => assert.fail('No debe enviar datos inválidos'));
  fill(get);
  get('password').value = 'é'.repeat(37);
  await submit();
  assert.match(get('password-error').textContent, /72 bytes/);
});

test('409 conserva los datos y permite corregir sin confirmar éxito', async () => {
  const { get, submit, location } = setup(async () => {
    throw new ApiError(new Response(null, { status: 409 }), null);
  });
  fill(get); await submit();
  assert.equal(location.target, undefined);
  assert.equal(get('nombre').value, ' Ana ');
  assert.equal(get('register-fields').disabled, false);
  assert.equal(get('register-error').hidden, false);
  assert.match(get('register-error-text').textContent, /email o DNI/);
});
