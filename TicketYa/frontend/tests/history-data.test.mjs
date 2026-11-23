import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseHistory, parsePurchase, pendingReservation, resumeLink, seedResume } from '../src/scripts/history-data.ts';
const purchase = { id: 40, idCliente: 7, idEvento: 700, idSector: 55, idReserva: 30, estadoCompra: 'PENDIENTE', cantidadEntradas: 2, medioDePago: 'SIMULADO', descripcion: 'Pendiente', claveIdempotencia: 'original', precioUnitario: 2500, importeTotal: 5000, idsAsientos: [1234, 9876] };
test('historial conserva estados e importes del servidor y rechaza otro perfil o respuesta inválida', () => {
  assert.deepEqual(parseHistory([purchase], 7), [purchase]);
  assert.deepEqual(parseHistory([], 7), []);
  for (const value of [null, [purchase, purchase], [{ ...purchase, idCliente: 8 }], [{ ...purchase, importeTotal: null }]]) assert.throws(() => parseHistory(value, 7));
  assert.equal(resumeLink(purchase), '/eventos/?id=700&compra=40#purchase-panel');
});
test('PENDIENTE puede tener reserva VENCIDA y no se transforma el estado de compra', () => {
  const r = pendingReservation({ id: 30, idCliente: 7, idEvento: 700, idSector: 55, estado: 'VENCIDA', venceEn: '2030-01-01T00:00:00Z' }, purchase);
  assert.equal(r.estado, 'VENCIDA'); assert.equal(purchase.estadoCompra, 'PENDIENTE');
  assert.throws(() => pendingReservation({ ...r, idSector: 99 }, purchase));
});
test('retomar conserva decisión e idempotencia y no reemplaza otro intento incierto', () => {
  const data = new Map();
  const storage = { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value) };
  const canonical = 'ticketya-compra-v1-7-700';
  storage.setItem(canonical, JSON.stringify({ body: { idCliente: 7, claveIdempotencia: 'original' }, paymentDecision: true, paymentUncertain: true }));
  assert.equal(seedResume(storage, purchase), canonical);
  const attempt = JSON.parse(storage.getItem(canonical));
  assert.equal(attempt.purchaseId, 40); assert.equal(attempt.paymentDecision, true); assert.equal(attempt.paymentUncertain, true);
  assert.equal(attempt.body.precioUnitario, undefined);
  const different = { ...purchase, id: 41, claveIdempotencia: 'otra' };
  assert.equal(seedResume(storage, different), canonical + '-compra-41');
  assert.equal(JSON.parse(storage.getItem(canonical)).body.claveIdempotencia, 'original');
});
