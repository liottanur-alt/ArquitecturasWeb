import { test } from 'node:test';
import assert from 'node:assert/strict';
import { purchaseFlow } from '../src/scripts/purchase-attempt.ts';
function setup() {
  const values = new Map();
  return { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) };
}
const selection = { idEvento: 700, idSector: 55, idsAsientos: [9876] };
const purchase = body => ({ id: 40, idReserva: 30, ...body, estadoCompra: 'PENDIENTE' });
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
    assert.equal(path, '/compras');
    assert.deepEqual(options.body, firstBody);
    assert.equal('precio' in options.body, false);
    return purchase(options.body);
  });
  const result = await restored.recover();
  assert.deepEqual(paths, ['/compras']);
  assert.equal(result.purchaseState, 'PENDIENTE');
  assert.equal(restored.canPay(true), true);
});
test('compra conocida se recupera por GET antes de habilitar el pago', async () => {
  const storage = setup();
  const first = purchaseFlow(storage, 'key', 7, 700, async (_path, options) => purchase(options.body));
  first.start(selection, 'clave');
  const result = await first.recover();
  const paths = [];
  const restored = purchaseFlow(storage, 'key', 7, 700, async path => {
    paths.push(path);
    return { ...purchase(result.body), estadoCompra: 'EXPIRADA' };
  });
  assert.equal(restored.canPay(true), false);
  await restored.recover(); assert.deepEqual(paths, ['/compras/40']);
  restored.reset(); assert.equal(restored.current, null);
});
test('409 permite revisar; un reintento incierto vuelve a impedir cambiar la clave', async () => {
  let fail = { status: 409 }; const flow = purchaseFlow(setup(), 'key', 7, 700, async () => { throw fail; });
  flow.start({ idEvento: 700, idSector: 55, cantidadEntradas: 2 }, 'campo');
  await assert.rejects(flow.recover()); assert.equal(flow.current.conflict, true);
  fail = new Error('conexión'); await assert.rejects(flow.recover()); assert.throws(() => flow.reset());
});
test('409 posterior a un POST incierto conserva el intento incluso tras recargar', async () => {
  const storage = setup();
  const flow = purchaseFlow(storage, 'key', 7, 700, async () => { throw new Error('timeout'); });
  flow.start(selection, 'clave');
  await assert.rejects(flow.recover(), /timeout/);
  const restored = purchaseFlow(storage, 'key', 7, 700, async () => { throw { status: 409 }; });
  await assert.rejects(restored.recover());
  assert.equal(restored.canReset(), false);
  assert.equal(restored.current.body.claveIdempotencia, 'clave');
});
test('un reintento incierto en fila conserva la autorización con el mismo pedido', async () => {
  const storage = setup();
  let original;
  const flow = purchaseFlow(storage, 'key', 7, 700, async (_path, options) => {
    original = options.body;
    assert.equal(original.autorizacionCompra, 'turno-admitido');
    assert.deepEqual(JSON.parse(storage.getItem('key')).body, original);
    throw new Error('timeout');
  });
  flow.start(selection, 'clave', 'turno-admitido');
  await assert.rejects(flow.recover(), /timeout/);
  const restored = purchaseFlow(storage, 'key', 7, 700, async (path, options) => {
    assert.equal(path, '/compras');
    assert.deepEqual(options.body, original);
    return purchase(options.body);
  });
  assert.throws(() => restored.start(selection, 'clave-nueva', 'turno-nuevo'));
  await restored.recover();
});
test('almacenamiento bloqueado impide POST; otro perfil no puede recuperar el intento', () => {
  const storage = setup(); const flow = purchaseFlow(storage, 'key', 7, 700, async () => assert.fail());
  flow.start(selection, 'clave'); assert.throws(() => purchaseFlow(storage, 'key', 8, 700, async () => {}));
  const blocked = purchaseFlow({ getItem: () => null, setItem: () => { throw Error('bloqueado'); }, removeItem() {} }, 'key', 7, 700, async () => assert.fail());
  assert.throws(() => blocked.start(selection, 'clave')); assert.equal(blocked.current, null);
});
