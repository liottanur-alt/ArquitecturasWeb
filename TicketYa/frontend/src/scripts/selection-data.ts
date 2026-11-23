export const positiveId = (value: unknown): value is number => Number.isSafeInteger(value) && Number(value) > 0;
export type Draft = { eventId: number; sectorId: number; type: 'CAMPO' | 'NUMERADO'; quantity: number; seatIds: number[]; updatedAt: string };
export type Seat = { idAsiento: number; fila: string; columna: string; estado: 'DISPONIBLE' | 'RESERVADO' | 'VENDIDO' };
export type Availability = { idEvento: number; idSector: number; tipo: Draft['type']; precio: number; disponibles: number; cancelado: boolean; asientos: Seat[] };
export function normalizeDraft(value: unknown): Draft | null {
  if (!value || typeof value !== 'object') return null;
  const v = value as Draft;
  if (!positiveId(v.eventId) || !positiveId(v.sectorId) || !['CAMPO', 'NUMERADO'].includes(v.type)
    || !Number.isInteger(v.quantity) || v.quantity < 1 || v.quantity > 1000 || !Array.isArray(v.seatIds)
    || !v.seatIds.every(positiveId) || new Set(v.seatIds).size !== v.seatIds.length
    || (v.type === 'CAMPO' ? v.seatIds.length !== 0 : v.seatIds.length !== v.quantity)
    || typeof v.updatedAt !== 'string' || !Number.isFinite(Date.parse(v.updatedAt))) return null;
  return { eventId: v.eventId, sectorId: v.sectorId, type: v.type, quantity: v.quantity, seatIds: [...v.seatIds], updatedAt: v.updatedAt };
}
export function parseAvailability(value: unknown, eventId: number, sectorId?: number): Availability {
  if (!value || typeof value !== 'object') throw new Error('Disponibilidad inválida.');
  const v = value as Availability;
  if (v.idEvento !== eventId || !positiveId(v.idSector) || (sectorId !== undefined && v.idSector !== sectorId)
    || !['CAMPO', 'NUMERADO'].includes(v.tipo) || typeof v.precio !== 'number' || !Number.isFinite(v.precio) || v.precio < 0
    || !Number.isInteger(v.disponibles) || v.disponibles < 0 || typeof v.cancelado !== 'boolean' || !Array.isArray(v.asientos)
    || !v.asientos.every(seat => seat && positiveId(seat.idAsiento) && typeof seat.fila === 'string' && typeof seat.columna === 'string'
      && ['DISPONIBLE', 'RESERVADO', 'VENDIDO'].includes(seat.estado))
    || new Set(v.asientos.map(seat => seat.idAsiento)).size !== v.asientos.length
    || (v.tipo === 'CAMPO' && v.asientos.length > 0)) throw new Error('El servidor devolvió una disponibilidad inválida.');
  return v;
}
export function reconcileDraft(draft: Draft, availability: Availability) {
  if (draft.eventId !== availability.idEvento || draft.sectorId !== availability.idSector || draft.type !== availability.tipo || availability.cancelado) {
    return { draft: null, changed: true };
  }
  const available = new Set(availability.asientos.filter(seat => seat.estado === 'DISPONIBLE').map(seat => seat.idAsiento));
  const seatIds = draft.seatIds.filter(id => available.has(id));
  const quantity = Math.min(draft.type === 'CAMPO' ? draft.quantity : seatIds.length, availability.disponibles, 1000);
  if (!quantity) return { draft: null, changed: true };
  const result = { ...draft, quantity, seatIds: seatIds.slice(0, quantity) };
  return { draft: result, changed: quantity !== draft.quantity || result.seatIds.length !== draft.seatIds.length };
}
export function saleOpen(value: unknown, now = Date.now()) {
  const v = value as { estado?: string; estadoPublicacion?: string; fechaHora?: string } | null;
  if (!v || v.estado !== 'EN_CURSO' || v.estadoPublicacion !== 'PUBLICADO' || typeof v.fechaHora !== 'string'
    || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(v.fechaHora)) return false;
  return Date.parse(v.fechaHora + '-03:00') > now;
}
/** Campos de selección de CompraRequest; no crea una compra ni una reserva. */
export function purchaseSelection(value: Draft) {
  const draft = normalizeDraft(value);
  if (!draft) throw new Error('Selección inválida.');
  return { idEvento: draft.eventId, idSector: draft.sectorId,
    ...(draft.type === 'CAMPO' ? { cantidadEntradas: draft.quantity } : { idsAsientos: [...draft.seatIds] }) };
}
export function eventLink(eventId: number, sectorId?: number) {
  return `/eventos/?id=${eventId}${sectorId === undefined ? '' : `&sector=${sectorId}&carrito=1`}`;
}
