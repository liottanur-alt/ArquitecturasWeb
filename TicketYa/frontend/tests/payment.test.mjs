import { test } from 'node:test';
import assert from 'node:assert/strict';
import { purchaseFlow } from '../src/scripts/purchase-attempt.ts';
function setup(state, paymentHandler) {
  const values = new Map(), calls = [];
  const storage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
  const body = { claveIdempotencia: 'clave', idCliente: 7, idEvento: 700, idSector: 55, medioDePago: 'SIMULADO', cantidadEntradas: 1 };
  const purchase = estadoCompra => ({ ...body, id: 40, idReserva: 30, estadoCompra });
  storage.setItem('key', JSON.stringify({ body, purchaseId: 40, reservationId: 30, purchaseState: 'PENDIENTE' }));
  const request = async (path, options) => {
    calls.push({ path, options });
    if (path.includes('/pago?')) return paymentHandler ? paymentHandler(path, options) : purchase(state);
    if (path === '/compras/40') return purchase(state);
    if (path === '/entradas/compra/40') return [{ id: 50, idCliente: 7, idEvento: 700, idSector: 55, estadoEntrada: 'PAGA', estadoQr: 'NO_ESCANEADO' }];
    return { id: 30, idCliente: 7, idEvento: 700, idSector: 55, estado: state === 'EXPIRADA' ? 'VENCIDA' : state === 'RECHAZADA' ? 'LIBERADA' : 'CONFIRMADA', venceEn: '2030-12-01T15:10:00Z' };
  };
  return { flow: purchaseFlow(storage, 'key', 7, 700, request), storage, calls, request, purchase };
}
for (const state of ['APROBADA', 'RECHAZADA', 'EXPIRADA']) test('HTTP 200 con ' + state + ' usa el estado de negocio y bloquea otro pago', async () => {
  const { flow, calls } = setup(state);
  const result = await flow.recover(state !== 'RECHAZADA');
  assert.equal(result.purchaseState, state);
  assert.equal(calls[0].path, '/compras/40/pago?aprobado=' + String(state !== 'RECHAZADA'));
  assert.deepEqual(calls[0].options, { method: 'PUT' });
  await assert.rejects(flow.recover(true));
  if (state === 'APROBADA') { assert.equal((await flow.entries()).length, 1); assert.equal(calls.at(-1).path, '/entradas/compra/40'); }
  else { await assert.rejects(flow.entries()); assert.equal(calls.some(call => call.path.startsWith('/entradas')), false); }
});
test('bloquea envíos simultáneos y decisiones incompatibles después de timeout y recarga', async () => {
  let reject;
  const { flow, storage, calls, request } = setup('APROBADA', () => new Promise((_, fail) => { reject = fail; }));
  const pending = flow.recover(true);
  await assert.rejects(flow.recover(true)); assert.equal(calls.length, 1);
  reject(new Error('timeout')); await assert.rejects(pending);
  assert.equal(flow.current.paymentUncertain, true); assert.throws(() => flow.reset());
  const restored = purchaseFlow(storage, 'key', 7, 700, request);
  await assert.rejects(restored.recover(false)); assert.equal(calls.length, 1);
  await restored.recover(); // Recuperar por GET, sin reenviar el pago.
  assert.equal(restored.current.purchaseState, 'APROBADA');
  assert.equal(calls.filter(call => call.path.includes('/pago?')).length, 1);
});
test('fallo de entradas no revierte aprobación ni habilita otro PUT', async () => {
  const { flow, storage, request } = setup('APROBADA'); await flow.recover(true);
  const restored = purchaseFlow(storage, 'key', 7, 700, (path, options) => { if (path.startsWith('/entradas')) throw new Error('sin conexión'); return request(path, options); });
  await assert.rejects(restored.entries(), /sin conexión/);
  await assert.rejects(restored.recover(false));
  assert.equal(restored.current.purchaseState, 'APROBADA');
});

test('una compra aprobada y reserva confirmada permiten preparar explícitamente otro sector con otra clave', async () => {
  const { flow } = setup('APROBADA');
  await flow.recover(true);
  flow.reset();
  flow.start({ idEvento: 700, idSector: 56, cantidadEntradas: 1 }, 'nuevo-sector');
  assert.equal(flow.current.body.idSector,56);
  assert.equal(flow.current.body.claveIdempotencia,'nuevo-sector');
});
