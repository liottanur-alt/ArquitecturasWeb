export type PurchaseBody = { claveIdempotencia: string; idCliente: number; idEvento: number; idSector: number; medioDePago: string; cantidadEntradas?: number; idsAsientos?: number[] };
export type Attempt = { body: PurchaseBody; purchaseId?: number; purchaseState?: string; reservationId?: number; reservationState?: string; expiresAt?: string; conflict?: boolean; paymentDecision?: boolean; paymentUncertain?: boolean };
const id = (value: unknown) => Number.isSafeInteger(value) && Number(value) > 0;
export function purchaseFlow(storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>, key: string, owner: number, eventId: number,
  request: (path: string, options?: { method?: string; body?: unknown; cache?: RequestCache }) => Promise<unknown>) {
  let attempt: Attempt | null = null;
  const raw = storage.getItem(key);
  if (raw) {
    const value = JSON.parse(raw) as Attempt, b = value?.body;
    if (!b || b.idCliente !== owner || b.idEvento !== eventId || !id(b.idSector) || typeof b.claveIdempotencia !== 'string' || !b.claveIdempotencia
      || Object.keys(b).some(key => !['claveIdempotencia','idCliente','idEvento','idSector','medioDePago','cantidadEntradas','idsAsientos'].includes(key)) || (typeof b.medioDePago !== 'string' || !b.medioDePago.trim()) || !validSelection(b)) throw new Error('El intento guardado no es válido. No se enviará una compra nueva.');
    if ((value.purchaseId !== undefined && !id(value.purchaseId)) || (value.reservationId !== undefined && !id(value.reservationId))) throw new Error('El intento guardado es inválido.');
    attempt = value;
  }
  let busy = false;
  function persist() { storage.setItem(key, JSON.stringify(attempt)); }
  return {
    get current() { return attempt ? structuredClone(attempt) : null; },
    start(selection: Omit<PurchaseBody, 'claveIdempotencia' | 'idCliente' | 'medioDePago'>, uuid: string) {
      if (attempt || storage.getItem(key)) throw new Error('Primero recuperá el resultado del intento guardado.');
      if (!uuid || uuid.length > 100 || selection.idEvento !== eventId || !id(owner) || !id(selection.idSector) || !validSelection(selection)) throw new Error('Selección inválida.');
      const body: PurchaseBody = { claveIdempotencia: uuid, idCliente: owner, idEvento: eventId, idSector: selection.idSector, medioDePago: 'SIMULADO',
        ...(selection.idsAsientos ? { idsAsientos: [...selection.idsAsientos] } : { cantidadEntradas: selection.cantidadEntradas }) };
      const next = { body };
      storage.setItem(key, JSON.stringify(next)); // Persistir ANTES de cualquier mutación remota.
      attempt = next;
    },
    reset() {
      if (busy || !attempt || attempt.paymentUncertain || !(attempt.conflict && !attempt.purchaseId || ['EXPIRADA', 'RECHAZADA', 'CANCELADA'].includes(attempt.purchaseState || '') || attempt.purchaseState === 'APROBADA' && attempt.reservationState === 'CONFIRMADA'
        || ['VENCIDA', 'LIBERADA', 'DEVUELTA'].includes(attempt.reservationState || ''))) throw new Error('El resultado todavía no permite un intento nuevo. Recuperá la compra y la reserva.');
      storage.removeItem(key); attempt = null;
    },
    async entries() {
      if (!attempt?.purchaseId || attempt.purchaseState !== 'APROBADA') throw new Error('Las entradas solo se consultan tras aprobación.');
      const data = await request('/entradas/compra/' + attempt.purchaseId, { cache: 'no-store' });
      if (!Array.isArray(data) || !data.every(entry => entry && id(entry.id) && entry.idCliente === owner && entry.idEvento === eventId && entry.idSector === attempt!.body.idSector && ['PAGA','SOLICITUD_DEVOLUCION','DEVOLUCION_ACEPTADA','DEVOLUCION_RECHAZADA','EVENTO_CANCELADO'].includes(entry.estadoEntrada) && ['NO_ESCANEADO','ESCANEADO'].includes(entry.estadoQr))) throw new Error('Respuesta de entradas inválida.');
      return data as { id: number; estadoEntrada: string; estadoQr: string }[];
    },
    async recover(payment?: boolean) {
      if (busy || !attempt) throw new Error('No hay un intento disponible o ya está en curso.');
      if (payment !== undefined && (!attempt.purchaseId || attempt.purchaseState !== 'PENDIENTE' || (attempt.paymentDecision !== undefined && attempt.paymentDecision !== payment))) throw new Error('El pago ya fue resuelto o la decisión no coincide con el intento conservado.');
      busy = true;
      try {
        if (payment !== undefined) { attempt.paymentDecision = payment; attempt.paymentUncertain = true; persist(); }
        attempt.conflict = false; persist();
        let data: unknown;
        try {
          data = payment !== undefined ? await request('/compras/' + attempt.purchaseId + '/pago?aprobado=' + String(payment), { method: 'PUT' }) : await request(attempt.purchaseId ? '/compras/' + attempt.purchaseId : '/compras', attempt.purchaseId ? { cache: 'no-store' } : { method: 'POST', body: structuredClone(attempt.body) });
        } catch (cause) {
          if (!attempt.purchaseId && (cause as { status?: number })?.status === 409) { attempt.conflict = true; persist(); }
          throw cause;
        }
        const purchase = data as Record<string, unknown>, b = attempt.body;
        if (!purchase || (attempt.purchaseId !== undefined && purchase.id !== attempt.purchaseId) || !id(purchase.id) || !id(purchase.idReserva) || purchase.idCliente !== b.idCliente || purchase.idEvento !== b.idEvento || purchase.idSector !== b.idSector
          || purchase.claveIdempotencia !== b.claveIdempotencia || !['PENDIENTE','APROBADA','RECHAZADA','EXPIRADA','CANCELADA'].includes(String(purchase.estadoCompra))) throw new Error('Respuesta de compra no verificable. Conservamos el intento; su resultado es incierto.');
        attempt.purchaseId = Number(purchase.id); attempt.reservationId = Number(purchase.idReserva); attempt.purchaseState = String(purchase.estadoCompra); attempt.conflict = false;
        if (attempt.purchaseState !== 'PENDIENTE') attempt.paymentUncertain = false;
        persist();
        const reservation = await request('/reservas/' + attempt.reservationId, { cache: 'no-store' }) as Record<string, unknown>;
        if (!reservation || reservation.id !== attempt.reservationId || reservation.idCliente !== b.idCliente || reservation.idEvento !== b.idEvento || reservation.idSector !== b.idSector
          || !['ACTIVA','CONFIRMADA','LIBERADA','DEVUELTA','VENCIDA'].includes(String(reservation.estado)) || typeof reservation.venceEn !== 'string' || !Number.isFinite(Date.parse(reservation.venceEn))) throw new Error('No se pudo verificar la reserva. Volvé a consultarla con la compra conocida.');
        attempt.reservationState = String(reservation.estado); attempt.expiresAt = reservation.venceEn; persist();
        return structuredClone(attempt);
      } finally { busy = false; }
    }
  };
}
function validSelection(b: { cantidadEntradas?: number; idsAsientos?: number[] }) {
  return b.idsAsientos !== undefined ? b.cantidadEntradas === undefined && Array.isArray(b.idsAsientos) && b.idsAsientos.length > 0 && b.idsAsientos.length <= 1000 && b.idsAsientos.every(id) && new Set(b.idsAsientos).size === b.idsAsientos.length
    : Number.isInteger(b.cantidadEntradas) && Number(b.cantidadEntradas) > 0 && Number(b.cantidadEntradas) <= 1000;
}
