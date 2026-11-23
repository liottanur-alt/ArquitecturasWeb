import { test } from 'node:test';
import assert from 'node:assert/strict';
import { normalizeDraft, parseAvailability, reconcileDraft, saleOpen, purchaseSelection, eventLink } from '../src/scripts/selection-data.ts';

const draft = { eventId: 701, sectorId: 55, type: 'NUMERADO', quantity: 2, seatIds: [1234, 9876], updatedAt: '2030-01-01T12:00:00Z' };
const availability = { idEvento: 701, idSector: 55, tipo: 'NUMERADO', precio: 1200, disponibles: 2, cancelado: false,
  asientos: [{ idAsiento: 1234, fila: 'Balcón', columna: 'X-7', estado: 'DISPONIBLE' }, { idAsiento: 9876, fila: 'Balcón', columna: 'X-8', estado: 'DISPONIBLE' }] };
test('selección de compra por sector: CAMPO cantidad, NUMERADO IDs físicos', () => {
  assert.deepEqual(purchaseSelection(draft), { idEvento: 701, idSector: 55, idsAsientos: [1234, 9876] });
  const campo = { ...draft, sectorId: 78, type: 'CAMPO', quantity: 3, seatIds: [] };
  assert.deepEqual(purchaseSelection(campo), { idEvento: 701, idSector: 78, cantidadEntradas: 3 });
  assert.equal(eventLink(701, 55), '/eventos/?id=701&sector=55&carrito=1');
});
test('descarta borradores de ejemplo, asientos duplicados y selecciones de más de un sector', () => {
  for (const invalid of [{ eventId: 1, selection: [{ sectorId: 'campo', quantity: 2, seats: [] }] },
    { ...draft, sectorId: '55' }, { ...draft, seatIds: [1234, 1234] }, { ...draft, quantity: 1001 },
    { ...draft, type: 'CAMPO' }, { ...draft, seatIds: [1234] }]) assert.equal(normalizeDraft(invalid), null);
  assert.deepEqual(normalizeDraft(draft), draft);
});
test('recuperación elimina asientos vendidos sin convertir filas/columnas en IDs', () => {
  assert.equal(reconcileDraft(draft, availability).changed, false);
  const result = reconcileDraft(draft, { ...availability, asientos: [{ ...availability.asientos[0], estado: 'VENDIDO' }, availability.asientos[1]] });
  assert.equal(result.changed, true);
  assert.deepEqual(result.draft.seatIds, [9876]);
  assert.equal(result.draft.quantity, 1);
  assert.equal(reconcileDraft(draft, { ...availability, cancelado: true }).draft, null);
  assert.equal(reconcileDraft(draft, { ...availability, idSector: 99 }).draft, null);
});
test('CAMPO se ajusta a cupo actual y no permite recuperar sin cupo', () => {
  const campo = { ...draft, type: 'CAMPO', quantity: 5, seatIds: [] };
  const current = { ...availability, tipo: 'CAMPO', asientos: [] };
  assert.equal(reconcileDraft(campo, current).draft.quantity, 2);
  assert.equal(reconcileDraft(campo, { ...current, disponibles: 0 }).draft, null);
});
test('valida disponibilidad para el evento/sector solicitado sin inventar precio o asientos', () => {
  assert.equal(parseAvailability(availability, 701, 55), availability);
  for (const value of [{ ...availability, precio: null }, { ...availability, disponibles: -1 }, { ...availability, idEvento: 1 }, { ...availability, asientos: [availability.asientos[0], availability.asientos[0]] }]) {
    assert.throws(() => parseAvailability(value, 701, 55));
  }
});
test('estado y fecha del evento impiden recuperar selecciones no vendibles', () => {
  const event = { estado: 'EN_CURSO', estadoPublicacion: 'PUBLICADO', fechaHora: '2030-12-20T21:00:00' };
  assert.equal(saleOpen(event, Date.parse('2030-12-20T23:59:59Z')), true);
  assert.equal(saleOpen(event, Date.parse('2030-12-21T00:00:00Z')), false);
  for (const estado of ['CANCELADO', 'FINALIZADO', 'AGOTADO']) assert.equal(saleOpen({ ...event, estado }, 0), false);
});
