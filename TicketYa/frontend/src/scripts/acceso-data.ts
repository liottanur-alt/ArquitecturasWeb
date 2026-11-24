export type AccessEntry = {
  id: number;
  idEvento: number;
  idSector: number;
  idCliente?: number;
  qr: string;
  estadoEntrada: string;
  estadoQr: 'ESCANEADO' | 'NO_ESCANEADO';
};

function positiveId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
}

export function accessRequest(eventId: number, input: string) {
  const qr = input.trim();
  if (!positiveId(eventId)) throw new Error('Ingresá el ID válido del evento.');
  if (!/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(qr)) {
    throw new Error('Ingresá la cadena del QR de una entrada de TicketYa.');
  }
  const path = '/entradas/qr/' + encodeURIComponent(qr);
  return { eventId, qr, path, markPath: path + '/uso?' + new URLSearchParams({ idEvento: String(eventId) }) };
}

export function parseAccessEntry(data: unknown, qr: string): AccessEntry {
  const entry = data as AccessEntry;
  if (!entry || entry.qr !== qr || !positiveId(entry.id) || !positiveId(entry.idEvento) || !positiveId(entry.idSector)
    || (entry.estadoQr !== 'ESCANEADO' && entry.estadoQr !== 'NO_ESCANEADO') || typeof entry.estadoEntrada !== 'string') {
    throw new Error('El servidor devolvió una entrada que no pudimos verificar.');
  }
  return entry;
}

export function accessDenial(entry: AccessEntry, eventId: number) {
  if (entry.idEvento !== eventId) return 'La entrada corresponde a otro evento.';
  if (entry.estadoEntrada === 'DEVOLUCION_ACEPTADA') return 'La entrada fue devuelta.';
  if (entry.estadoEntrada === 'DEVOLUCION_RECHAZADA') return 'La devolución fue rechazada y la entrada no está vigente.';
  if (entry.estadoEntrada === 'SOLICITUD_DEVOLUCION') return 'La entrada tiene una devolución pendiente.';
  if (entry.estadoEntrada === 'EVENTO_CANCELADO') return 'El evento fue cancelado.';
  if (entry.estadoEntrada !== 'PAGA') return 'La entrada no está vigente.';
  if (entry.estadoQr === 'ESCANEADO') return 'El ingreso de esta entrada ya fue registrado.';
  return null;
}
