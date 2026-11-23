import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseRefunds, refundRule } from '../src/scripts/refund-data.ts';
const entry = { id: 50, idCliente: 7, estadoEntrada: 'PAGA', estadoQr: 'ESCANEADO' };
const event = { estado: 'EN_CURSO', fechaHora: '2030-12-20T21:00:00' };
const request = { id: 60, idEntrada: 50, idCompra: 40, idCliente: 7, descripcion: 'No puedo ir', estadoSolicitud: 'PENDIENTE', fechaSolicitud: '2030-01-01T00:00:00Z' };
test('plazo de 72 horas es inclusivo y usa Buenos Aires sin depender del equipo', () => {
  const deadline = Date.parse('2030-12-18T00:00:00Z');
  assert.equal(refundRule(entry, [], event, deadline).allowed, true);
  assert.equal(refundRule(entry, [], event, deadline + 1).allowed, false);
});
test('pendiente o aceptada impiden otra solicitud; rechazada admite solicitar de nuevo', () => {
  for (const estadoSolicitud of ['PENDIENTE','ACEPTADA']) assert.equal(refundRule(entry, [{ ...request, estadoSolicitud }], event, 0).allowed, false);
  assert.equal(refundRule({ ...entry, estadoEntrada: 'DEVOLUCION_RECHAZADA' }, [{ ...request, estadoSolicitud: 'RECHAZADA' }], event, 0).allowed, true);
  assert.equal(refundRule({ ...entry, estadoEntrada: 'SOLICITUD_DEVOLUCION' }, [], event, 0).allowed, false);
});
test('sin fecha no inventa plazo ni bloqueo; cancelado/finalizado no permite solicitar', () => {
  assert.match(refundRule(entry, [], null).message, /servidor comprobará/);
  for (const estado of ['CANCELADO','FINALIZADO']) assert.equal(refundRule(entry, [], { ...event, estado }, 0).allowed, false);
  assert.equal(refundRule(entry, [], event, 0).allowed, true); // El contrato no excluye QR escaneado.
});
test('conserva estados reales y rechaza consultas ajenas, duplicadas o inválidas', () => {
  for (const estadoSolicitud of ['PENDIENTE','ACEPTADA','RECHAZADA','CANCELADA']) assert.equal(parseRefunds([{ ...request, estadoSolicitud }], 7)[0].estadoSolicitud, estadoSolicitud);
  for (const value of [null, [request, request], [{ ...request, idCliente: 8 }], [{ ...request, estadoSolicitud: 'APROBADA' }]]) assert.throws(() => parseRefunds(value, 7));
});
