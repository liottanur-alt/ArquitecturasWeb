export type Purchase = { id: number; idCliente: number; idEvento: number; idSector: number; idReserva: number; estadoCompra: string; cantidadEntradas: number; medioDePago: string; descripcion: string; claveIdempotencia: string; precioUnitario: number; importeTotal: number; idsAsientos: number[]; referenciaPago?: string | null; referenciaReintegro?: string | null; estadoReintegro?: string | null; fechaCreacion?: string | null };
const id = (v: unknown) => Number.isSafeInteger(v) && Number(v) > 0;
export function parsePurchase(value: unknown, owner: number): Purchase {
  const p = value as Purchase;
  if (!p || ![p.id,p.idCliente,p.idEvento,p.idSector,p.idReserva,p.cantidadEntradas].every(id) || p.idCliente !== owner
    || !['PENDIENTE','APROBADA','RECHAZADA','EXPIRADA','CANCELADA'].includes(p.estadoCompra)
    || ![p.precioUnitario,p.importeTotal].every(v => typeof v === 'number' && Number.isFinite(v) && v >= 0)
    || typeof p.medioDePago !== 'string' || !p.medioDePago.trim() || typeof p.claveIdempotencia !== 'string' || !p.claveIdempotencia
    || (p.fechaCreacion != null && (typeof p.fechaCreacion !== 'string' || !Number.isFinite(Date.parse(p.fechaCreacion))))
    || !Array.isArray(p.idsAsientos) || !p.idsAsientos.every(id) || new Set(p.idsAsientos).size !== p.idsAsientos.length
    || p.idsAsientos.length > 0 && p.idsAsientos.length !== p.cantidadEntradas) throw new Error('El servidor devolvió una compra inválida o de otro perfil.');
  return p;
}
export function parseHistory(value: unknown, owner: number) {
  if (!Array.isArray(value)) throw new Error('El historial recibido no es una lista válida.');
  const result = value.map(p => parsePurchase(p, owner));
  if (new Set(result.map(p => p.id)).size !== result.length) throw new Error('El historial contiene compras duplicadas.');
  return result;
}
export function resumeLink(p: Purchase) { return '/eventos/?id=' + p.idEvento + '&compra=' + p.id + '#purchase-panel'; }
export function seedResume(storage: Pick<Storage,'getItem'|'setItem'>, p: Purchase) {
  const canonical = 'ticketya-compra-v1-' + p.idCliente + '-' + p.idEvento;
  const old = storage.getItem(canonical);
  const saved = old ? JSON.parse(old) : null;
  const key = saved?.body?.claveIdempotencia === p.claveIdempotencia && saved?.body?.idCliente === p.idCliente ? canonical : canonical + '-compra-' + p.id;
  const existing = key === canonical ? saved : JSON.parse(storage.getItem(key) || 'null');
  if (existing && (existing.body?.claveIdempotencia !== p.claveIdempotencia || existing.body?.idCliente !== p.idCliente)) throw new Error('El intento local no coincide con la compra elegida.');
  storage.setItem(key, JSON.stringify({ ...existing, body: { claveIdempotencia: p.claveIdempotencia, idCliente: p.idCliente, idEvento: p.idEvento, idSector: p.idSector, medioDePago: p.medioDePago,
    ...(p.idsAsientos.length ? { idsAsientos: p.idsAsientos } : { cantidadEntradas: p.cantidadEntradas }),
    ...(existing?.body?.autorizacionCompra ? { autorizacionCompra: existing.body.autorizacionCompra } : {}) }, purchaseId: p.id, reservationId: p.idReserva, purchaseState: p.estadoCompra }));
  return key;
}
