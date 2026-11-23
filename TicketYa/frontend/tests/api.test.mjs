import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { api, ApiError, ApiConnectionError } from '../src/scripts/api.ts';

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
