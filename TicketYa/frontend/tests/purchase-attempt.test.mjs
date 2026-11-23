import { test } from 'node:test';
import assert from 'node:assert/strict';
import { purchaseFlow } from '../src/scripts/purchase-attempt.ts';
function setup() {
  const values = new Map();
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
}
const selection = { idEvento: 700, idSector: 55, idsAsientos: [9876] };
const purchase = body => ({ id: 40, idReserva: 30, ...body, estadoCompra: 'PENDIENTE' });
const reservation = { id: 30, idCliente: 7, idEvento: 700, idSector: 55, estado: 'ACTIVA', venceEn: '2030-12-01T15:10:00Z' };
test('timeout y recarga conservan clave y cuerpo; no envía precios ni crea reserva aparte', async () => {
  const storage = setup(); let firstBody;
  const first = purchaseFlow(storage, 'key', 7, 700, async (path, options) => {
    assert.equal(path, '/compras'); firstBody = options.body;
    assert.ok(storage.getItem('key')); throw new Error('timeout');
  });
  first.start(selection, 'clave-original'); await assert.rejects(first.recover(), /timeout/);
  assert.throws(() => first.reset()); assert.throws(() => first.start(selection, 'otra-clave'));
  const paths = [];
  const restored = purchaseFlow(storage, 'key', 7, 700, async (path, options) => {
    paths.push(path);
    if (path === '/compras') { assert.deepEqual(options.body, firstBody); assert.equal('precio' in options.body, false); return purchase(options.body); }
    return reservation;
  });
  const result = await restored.recover();
  assert.deepEqual(paths, ['/compras', '/reservas/30']);
  assert.equal(result.reservationState, 'ACTIVA'); assert.equal(result.expiresAt, reservation.venceEn);
});
test('fallo al leer reserva conserva compra conocida y recupera por GET', async () => {
  const storage = setup(); let body;
  const first = purchaseFlow(storage, 'key', 7, 700, async (path, options) => {
    if (path === '/compras') { body = options.body; return purchase(body); }
    throw new Error('reserva inaccesible');
  });
  first.start(selection, 'clave'); await assert.rejects(first.recover());
  const paths = [];
  const restored = purchaseFlow(storage, 'key', 7, 700, async path => { paths.push(path); return path === '/compras/40' ? purchase(body) : { ...reservation, estado: 'VENCIDA' }; });
  await restored.recover(); assert.deepEqual(paths, ['/compras/40', '/reservas/30']);
  restored.reset(); assert.equal(restored.current, null);
});
test('409 permite revisar; un reintento incierto vuelve a impedir cambiar la clave', async () => {
  let fail = { status: 409 }; const flow = purchaseFlow(setup(), 'key', 7, 700, async () => { throw fail; });
  flow.start({ idEvento: 700, idSector: 55, cantidadEntradas: 2 }, 'campo');
  await assert.rejects(flow.recover()); assert.equal(flow.current.conflict, true);
  fail = new Error('conexión'); await assert.rejects(flow.recover()); assert.throws(() => flow.reset());
});
test('almacenamiento bloqueado impide POST; otro perfil no puede recuperar el intento', () => {
  const storage = setup(); const flow = purchaseFlow(storage, 'key', 7, 700, async () => assert.fail());
  flow.start(selection, 'clave'); assert.throws(() => purchaseFlow(storage, 'key', 8, 700, async () => {}));
  const blocked = purchaseFlow({ getItem: () => null, setItem: () => { throw Error('bloqueado'); }, removeItem() {} }, 'key', 7, 700, async () => assert.fail());
  assert.throws(() => blocked.start(selection, 'clave')); assert.equal(blocked.current, null);
});
