import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import vm from 'node:vm';
import * as helpers from '../src/scripts/profile-data.ts';
import { ApiError } from '../src/scripts/api.ts';

const source = stripTypeScriptTypes(readFileSync(new URL('../src/scripts/profile.ts', import.meta.url), 'utf8').replace(/^import .*;\r?\n/gm, ''));
const original = { id: 18, idRol: 93, nombre: 'Ana', email: 'ana@example.com', dni: 'AB-123', ciudad: 'Tandil' };
const settle = () => new Promise(resolve => setImmediate(resolve));
function setup(save) {
  let owner = 18;
  const elements = new Map();
  const get = id => {
    if (!elements.has(id)) elements.set(id, {
      value: '', disabled: false, hidden: false, handlers: {}, textContent: '',
      addEventListener(name, handler) { this.handlers[name] = handler; },
      setAttribute() {}, removeAttribute() {}, setCustomValidity() {},
      querySelectorAll: () => [], reportValidity() {},
    });
    return elements.get(id);
  };
  const calls = [];
  vm.runInNewContext(source, {
    ...helpers, ApiError,
    api: async (path, options) => { calls.push({ path, options }); return options ? save(options) : { data: { ...original } }; },
    getAuthenticatedUserId: () => owner, getSession: () => ({ roles: ['CLIENTE'] }), clearSession() { owner = null; },
    initTickets() {}, initCart() {}, initHistory() {}, initPreferences() {}, initRecommendations() {}, getCart: () => [], catalog: [], demoPurchases: [],
    document: { querySelector: selector => get(selector), getElementById: id => get('#' + id), querySelectorAll: () => [], addEventListener() {} },
    window: { addEventListener() {} }, history: { replaceState() {} },
    location: { hash: '' }, matchMedia: () => ({ matches: true }),
  });
  return { get, calls, setOwner: id => { owner = id; }, submit: () => get('#profile-form').handlers.submit({ preventDefault() {} }) };
}
test('GET habilita edición y PUT conserva el rol recibido sin consultar roles', async () => {
  let finish;
  const { get, calls, submit } = setup(options => new Promise(resolve => { finish = () => resolve({ data: { id: 18, ...options.body } }); }));
  assert.equal(get('#profile-fields').disabled, true);
  await settle();
  assert.equal(get('#profile-fields').disabled, false);
  get('#nombre').value = ' Ana editada ';
  const pending = submit();
  assert.equal(get('#profile-fields').disabled, true);
  await submit();
  assert.equal(calls.length, 2);
  assert.equal(calls[0].path, '/usuarios/18');
  assert.equal(calls[1].options.method, 'PUT');
  assert.equal(calls[1].options.body.idRol, 93);
  assert.equal(calls[1].options.body.nombre, 'Ana editada');
  finish(); await pending;
  assert.equal(get('#profile-fields').disabled, false);
  assert.equal(get('#save-profile-label').textContent, 'Guardar cambios');
});
test('un 409 conserva la edición y permite corregir', async () => {
  const { get, submit } = setup(() => { throw new ApiError(new Response(null, { status: 409 }), null); });
  await settle(); get('#email').value = 'duplicado@example.com'; await submit();
  assert.equal(get('#email').value, 'duplicado@example.com');
  assert.equal(get('#profile-fields').disabled, false);
  assert.match(get('#profile-error-text').textContent, /email o DNI/);
});

test('una respuesta de guardado pendiente no actualiza el perfil al cambiar de cuenta', async () => {
  let finish;
  const { get, submit, setOwner } = setup(() => new Promise(resolve => { finish = resolve; }));
  await settle();
  const pending = submit();
  setOwner(19);
  finish({ data: { ...original, nombre: 'Respuesta anterior' } });
  await pending;
  assert.equal(get('#nombre').value, original.nombre);
  assert.equal(get('#profile-fields').disabled, true);
});
