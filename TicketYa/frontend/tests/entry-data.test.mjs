import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseEntry, parseEntries, qrEligible, filterEntry, entryStates } from '../src/scripts/entry-data.ts';
const entry = { id: 50, qr: ' exact-server-value:ñ /+\n', estadoQr: 'NO_ESCANEADO', idCliente: 7, idEvento: 700, idSector: 55, idAsiento: 9876, precio: 1500, estadoEntrada: 'PAGA' };
test('preserva cadena QR exacta y IDs sin generar payload local', () => {
  assert.equal(parseEntry(entry, 7).qr, entry.qr);
  assert.equal(parseEntries([entry], 7)[0].idAsiento, 9876);
  assert.equal(parseEntry({ ...entry, idAsiento: null }, 7).idAsiento, null);
});
test('solo PAGA y NO_ESCANEADO habilita QR, sin reinterpretar devolución rechazada', () => {
  assert.equal(qrEligible(entry), true);
  assert.equal(qrEligible({ ...entry, estadoQr: 'ESCANEADO' }), false);
  for (const estadoEntrada of entryStates.filter(v => v !== 'PAGA')) assert.equal(qrEligible({ ...entry, estadoEntrada }), false);
});
test('filtra estado de entrada y escaneo por separado', () => {
  assert.equal(filterEntry(entry, 'PAGA'), true);
  assert.equal(filterEntry(entry, 'scan:NO_ESCANEADO'), true);
  assert.equal(filterEntry(entry, 'scan:ESCANEADO'), false);
});
test('rechaza respuestas inválidas, duplicados y entradas de otro perfil', () => {
  for (const value of [null, [entry, entry], [{ ...entry, idCliente: 8 }], [{ ...entry, estadoQr: 'USADA' }], [{ ...entry, idAsiento: 'A-1' }], [{ ...entry, qr: '' }]]) assert.throws(() => parseEntries(value, 7));
  assert.deepEqual(parseEntries([], 7), []);
});
