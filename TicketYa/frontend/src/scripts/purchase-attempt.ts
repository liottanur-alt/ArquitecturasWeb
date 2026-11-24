export type PurchaseBody = { claveIdempotencia: string; idCliente: number; idEvento: number; idSector: number; medioDePago: string; cantidadEntradas?: number; idsAsientos?: number[]; autorizacionCompra?: string };
export type Attempt = { body: PurchaseBody; purchaseId?: number; purchaseState?: string; conflict?: boolean; creationUncertain?: boolean; paymentDecision?: boolean; paymentUncertain?: boolean };
const terminalStates = ['APROBADA', 'RECHAZADA', 'EXPIRADA', 'CANCELADA'];
const id = (value: unknown) => Number.isSafeInteger(value) && Number(value) > 0;
export function purchaseFlow(storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>, key: string, owner: number, eventId: number,
  request: (path: string, options?: { method?: string; body?: unknown; cache?: RequestCache }) => Promise<unknown>) {
  let attempt: Attempt | null = null;
  const raw = storage.getItem(key);
  if (raw) {
    const value = JSON.parse(raw) as Attempt, b = value?.body;
    if (!b || b.idCliente !== owner || b.idEvento !== eventId || !id(b.idSector) || typeof b.claveIdempotencia !== 'string' || !b.claveIdempotencia
      || Object.keys(b).some(key => !['claveIdempotencia','idCliente','idEvento','idSector','medioDePago','cantidadEntradas','idsAsientos','autorizacionCompra'].includes(key)) || (typeof b.medioDePago !== 'string' || !b.medioDePago.trim()) || !validSelection(b)) throw new Error('El intento guardado no es válido. No se enviará una compra nueva.');
    if (value.purchaseId !== undefined && !id(value.purchaseId)) throw new Error('El intento guardado es inválido.');
    const { purchaseId, purchaseState, conflict, paymentDecision, paymentUncertain } = value;
    // Una versión anterior no distinguía un POST pendiente de uno todavía sin enviar.
    const creationUncertain = value.creationUncertain ?? !purchaseId;
    attempt = { body: b, purchaseId, purchaseState, conflict, creationUncertain, paymentDecision, paymentUncertain };
  }
  let busy = false;
  let verified = false;
  function persist() { storage.setItem(key, JSON.stringify(attempt)); }
  function canReset() {
    return !!attempt && !busy && !attempt.paymentUncertain
      && (!!attempt.conflict && !attempt.purchaseId && !attempt.creationUncertain || terminalStates.includes(attempt.purchaseState ?? ''));
  }
  function canPay(payment: boolean) {
    return !!attempt?.purchaseId && !busy && verified && attempt.purchaseState === 'PENDIENTE'
      && (attempt.paymentDecision === undefined || attempt.paymentDecision === payment);
  }
  return {
    get current() { return attempt ? structuredClone(attempt) : null; },
    canReset,
    canPay,
    start(selection: Omit<PurchaseBody, 'claveIdempotencia' | 'idCliente' | 'medioDePago' | 'autorizacionCompra'>, uuid: string, authorization?: string) {
      if (attempt || storage.getItem(key)) throw new Error('Primero recuperá el resultado del intento guardado.');
      if (!uuid || uuid.length > 100 || selection.idEvento !== eventId || !id(owner) || !id(selection.idSector)
        || !validSelection({ ...selection, autorizacionCompra: authorization })) throw new Error('Selección o autorización inválida.');
      const body: PurchaseBody = { claveIdempotencia: uuid, idCliente: owner, idEvento: eventId, idSector: selection.idSector, medioDePago: 'SIMULADO',
        ...(selection.idsAsientos ? { idsAsientos: [...selection.idsAsientos] } : { cantidadEntradas: selection.cantidadEntradas }),
        ...(authorization === undefined ? {} : { autorizacionCompra: authorization }) };
      const next = { body, creationUncertain: false };
      storage.setItem(key, JSON.stringify(next)); // Persistir ANTES de cualquier mutación remota.
      attempt = next;
    },
    reset() {
      if (!canReset()) throw new Error('El resultado todavía no permite un intento nuevo. Recuperá la compra.');
      storage.removeItem(key); attempt = null; verified = false;
    },
    async entries() {
      if (!attempt?.purchaseId || !verified || attempt.purchaseState !== 'APROBADA') throw new Error('Las entradas solo se consultan tras verificar la aprobación.');
      const data = await request('/entradas/compra/' + attempt.purchaseId, { cache: 'no-store' });
      if (!Array.isArray(data) || !data.every(entry => entry && id(entry.id) && entry.idCliente === owner && entry.idEvento === eventId && entry.idSector === attempt!.body.idSector && ['PAGA','SOLICITUD_DEVOLUCION','DEVOLUCION_ACEPTADA','DEVOLUCION_RECHAZADA','EVENTO_CANCELADO'].includes(entry.estadoEntrada) && ['NO_ESCANEADO','ESCANEADO'].includes(entry.estadoQr))) throw new Error('Respuesta de entradas inválida.');
      return data as { id: number; estadoEntrada: string; estadoQr: string }[];
    },
    async recover(payment?: boolean) {
      if (busy || !attempt) throw new Error('No hay un intento disponible o ya está en curso.');
      if (payment !== undefined && !canPay(payment)) throw new Error('Consultá el estado de la compra antes de pagar y conservá la decisión del intento anterior.');
      busy = true;
      verified = false;
      try {
        if (payment !== undefined) { attempt.paymentDecision = payment; attempt.paymentUncertain = true; }
        const previousCreationUncertain = attempt.creationUncertain;
        if (!attempt.purchaseId) attempt.creationUncertain = true;
        attempt.conflict = false;
        persist();
        let data: unknown;
        try {
          data = payment !== undefined ? await request('/compras/' + attempt.purchaseId + '/pago?aprobado=' + String(payment), { method: 'PUT' }) : await request(attempt.purchaseId ? '/compras/' + attempt.purchaseId : '/compras', attempt.purchaseId ? { cache: 'no-store' } : { method: 'POST', body: structuredClone(attempt.body) });
        } catch (cause) {
          if (!attempt.purchaseId && !previousCreationUncertain && (cause as { status?: number })?.status === 409) {
            attempt.conflict = true;
            attempt.creationUncertain = false;
            persist();
          }
          throw cause;
        }
        const purchase = data as Record<string, unknown>, b = attempt.body;
        if (!purchase || (attempt.purchaseId !== undefined && purchase.id !== attempt.purchaseId) || !id(purchase.id) || purchase.idCliente !== b.idCliente || purchase.idEvento !== b.idEvento || purchase.idSector !== b.idSector
          || purchase.claveIdempotencia !== b.claveIdempotencia || typeof purchase.estadoCompra !== 'string'
          || !(purchase.estadoCompra === 'PENDIENTE' || terminalStates.includes(purchase.estadoCompra))) throw new Error('Respuesta de compra no verificable. Conservamos el intento; su resultado es incierto.');
        attempt.purchaseId = Number(purchase.id); attempt.purchaseState = purchase.estadoCompra;
        attempt.creationUncertain = false;
        if (attempt.purchaseState !== 'PENDIENTE') attempt.paymentUncertain = false;
        persist();
        verified = true;
        return structuredClone(attempt);
      } finally { busy = false; }
    }
  };
}
function validSelection(b: { cantidadEntradas?: number; idsAsientos?: number[]; autorizacionCompra?: string }) {
  if (b.autorizacionCompra !== undefined && (typeof b.autorizacionCompra !== 'string' || !b.autorizacionCompra.trim())) return false;
  return b.idsAsientos !== undefined ? b.cantidadEntradas === undefined && Array.isArray(b.idsAsientos) && b.idsAsientos.length > 0 && b.idsAsientos.length <= 1000 && b.idsAsientos.every(id) && new Set(b.idsAsientos).size === b.idsAsientos.length
    : Number.isInteger(b.cantidadEntradas) && Number(b.cantidadEntradas) > 0 && Number(b.cantidadEntradas) <= 1000;
}
