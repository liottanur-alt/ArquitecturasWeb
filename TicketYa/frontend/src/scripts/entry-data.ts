export const entryStates = ['PAGA','SOLICITUD_DEVOLUCION','DEVOLUCION_ACEPTADA','DEVOLUCION_RECHAZADA','EVENTO_CANCELADO'] as const;
export type Entry = { id: number; qr: string; estadoQr: 'NO_ESCANEADO' | 'ESCANEADO'; idCliente: number; idEvento: number; idSector: number; idAsiento: number | null; precio: number; estadoEntrada: string };
const id = (v: unknown) => Number.isSafeInteger(v) && Number(v) > 0;
export function parseEntry(value: unknown, owner: number): Entry {
  const entry = value as Entry;
  if (!entry || ![entry.id,entry.idCliente,entry.idEvento,entry.idSector].every(id) || entry.idCliente !== owner
    || entry.idAsiento !== null && !id(entry.idAsiento) || typeof entry.qr !== 'string' || !entry.qr.trim()
    || !['NO_ESCANEADO','ESCANEADO'].includes(entry.estadoQr) || !entryStates.includes(entry.estadoEntrada as typeof entryStates[number])
    || typeof entry.precio !== 'number' || !Number.isFinite(entry.precio) || entry.precio < 0) throw new Error('Entrada inválida o de otro perfil.');
  return entry;
}
export function parseEntries(value: unknown, owner: number) {
  if (!Array.isArray(value)) throw new Error('La respuesta de entradas no es una lista válida.');
  const entries = value.map(v => parseEntry(v, owner));
  if (new Set(entries.map(e => e.id)).size !== entries.length) throw new Error('Se recibieron entradas duplicadas.');
  return entries;
}
export function qrEligible(entry: Entry) { return entry.estadoEntrada === 'PAGA' && entry.estadoQr === 'NO_ESCANEADO'; }
export function filterEntry(entry: Entry, filter: string) { return !filter || (filter.startsWith('scan:') ? entry.estadoQr === filter.slice(5) : entry.estadoEntrada === filter); }
