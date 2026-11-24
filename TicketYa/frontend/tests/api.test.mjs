import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { api, ApiError, ApiConnectionError } from '../src/scripts/api.ts';
import { getSession, startSession } from '../src/scripts/session.ts';

const originalFetch = globalThis.fetch;
after(() => { globalThis.fetch = originalFetch; });

test('conserva rutas Gateway y no agrega credenciales', async () => {
  for (const path of ['/api/eventos', '/usuarios', '/eventos/10/sectores']) {
    globalThis.fetch = async (url, options) => {
      assert.equal(url, `/gateway${path}`);
      assert.equal(options.credentials, 'omit');
      assert.equal(options.headers.has('Authorization'), false);
      return Response.json([]);
    };
    assert.deepEqual((await api(path)).data, []);
  }
});

test('serializa JSON y admite 204 sin parsear cuerpo', async () => {
  globalThis.fetch = async (_, options) => {
    assert.equal(options.method, 'PUT');
    assert.equal(options.body, '{"nombre":"Ana"}');
    assert.equal(options.headers.get('Content-Type'), 'application/json');
    return new Response(null, { status: 204 });
  };
  const result = await api('/usuarios/1', { method: 'PUT', body: { nombre: 'Ana' } });
  assert.equal(result.data, null);
  assert.equal(result.status, 204);
});

test('expone errores JSON heterogéneos y metadatos', async () => {
  for (const payload of [{ detail: 'Ocupado' }, { error: 'Ocupado', correlationId: 'id-1' }, { message: 'Ocupado' }]) {
    globalThis.fetch = async () => Response.json(payload, { status: 503, headers: { 'Retry-After': '1' } });
    await assert.rejects(api('/compras'), error => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, 503);
      assert.equal(error.message, 'Ocupado');
      assert.equal(error.retryAfter, '1');
      assert.deepEqual(error.payload, payload);
      return true;
    });
  }
});

test('admite texto y errores vacíos del proxy', async () => {
  globalThis.fetch = async () => new Response('Gateway no disponible', { status: 502 });
  await assert.rejects(api('/usuarios'), { message: 'Gateway no disponible', status: 502 });
  globalThis.fetch = async () => new Response('', { status: 500 });
  await assert.rejects(api('/usuarios'), { message: 'HTTP 500' });
  globalThis.fetch = async () => new Response('texto');
  assert.equal((await api('/usuarios')).data, 'texto');
});

test('no reintenta conexión, abortos ni lecturas fallidas', async () => {
  for (const reason of [new TypeError('Failed to fetch'), new DOMException('Abortado', 'AbortError')]) {
    let calls = 0;
    globalThis.fetch = async () => { calls++; throw reason; };
    await assert.rejects(api('/compras', { method: 'POST', body: {} }), error => {
      assert.ok(error instanceof ApiConnectionError);
      assert.equal(error.reason, reason);
      return true;
    });
    assert.equal(calls, 1);
  }
  globalThis.fetch = async () => ({ status: 200, text: async () => { throw new TypeError('connection lost'); } });
  await assert.rejects(api('/usuarios'), ApiConnectionError);
});

test('rechaza URLs ajenas al Gateway', async () => {
  globalThis.fetch = async () => assert.fail('No debe consultar una URL inválida');
  for (const path of ['https://example.com', '//example.com', '/api/../login']) {
    await assert.rejects(api(path));
  }
});

test('envía Bearer al Gateway, trata 401 protegido y conserva la sesión ante 403 o login fallido', async t => {
  const data = new Map();
  globalThis.sessionStorage = {
    getItem: key => data.get(key) ?? null,
    setItem: (key, value) => data.set(key, value),
    removeItem: key => data.delete(key),
  };
  globalThis.window = new EventTarget();
  window.location = {
    origin: 'https://ticketya.example', pathname: '/perfil/', search: '', hash: '#compras',
    replace(value) { this.target = value; }
  };
  t.after(() => { delete globalThis.sessionStorage; delete globalThis.window; });
  const payload = Buffer.from(JSON.stringify({ sub: '7', exp: Math.floor(Date.now() / 1000) + 120 })).toString('base64url');
  const accessToken = `e30.${payload}.fixture`;
  startSession({ accessToken });

  globalThis.fetch = async (_, options) => {
    assert.equal(options.headers.get('Authorization'), `Bearer ${accessToken}`);
    return new Response(null, { status: 403 });
  };
  await assert.rejects(api('/roles'), { status: 403 });
  assert.equal(getSession()?.userId, 7);

  globalThis.fetch = async (_, options) => {
    assert.equal(options.headers.has('Authorization'), false);
    return new Response(null, { status: 401 });
  };
  await assert.rejects(api('/auth/login', { method: 'POST', body: {} }), { status: 401 });
  assert.equal(getSession()?.userId, 7);
  assert.equal(window.location.target, undefined);

  globalThis.fetch = async () => new Response(null, { status: 401 });
  await assert.rejects(api('/usuarios/7'), { status: 401 });
  assert.equal(getSession(), null);
  assert.equal(window.location.target, '/login/?volver=%2Fperfil%2F%23compras');
});
