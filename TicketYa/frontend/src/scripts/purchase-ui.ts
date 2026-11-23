import { parsePurchase, seedResume } from './history-data';
import { api, ApiError } from './api';
import { purchaseFlow } from './purchase-attempt';
import { purchaseSelection, type Draft } from './selection-data';
import { getSelectedProfileId } from './session';
export function initPurchase(owner: number | null, eventId: number, getDraft: () => Draft | null, conflict: () => void) {
  const create = document.querySelector<HTMLButtonElement>('#create-purchase')!;
  const recover = document.querySelector<HTMLButtonElement>('#recover-purchase')!;
  const reset = document.querySelector<HTMLButtonElement>('#new-purchase')!;
  const status = document.querySelector<HTMLElement>('#purchase-status')!;
  const reservation = document.querySelector<HTMLElement>('#reservation-status')!;
  const approve = document.querySelector<HTMLButtonElement>('#approve-payment')!;
  const reject = document.querySelector<HTMLButtonElement>('#reject-payment')!;
  const paymentState = document.querySelector<HTMLElement>('#payment-state')!;
  const entriesStatus = document.querySelector<HTMLElement>('#payment-entries-status')!;
  const entriesList = document.querySelector<HTMLElement>('#payment-entries')!;
  const clock = document.querySelector<HTMLElement>('#reservation-clock')!;
  let flow: ReturnType<typeof purchaseFlow> | undefined, busy = false;
  let lastError = '';
  const resumeParam = new URLSearchParams(location.search).get('compra');
  const resumeId = resumeParam === null ? null : Number(resumeParam);
  let resumeVerified = resumeId === null;
  const back = document.querySelector<HTMLElement>('#back-history'); if (back) back.hidden = resumeId === null;
  try {
    if (owner !== null && resumeId === null) flow = purchaseFlow(localStorage, 'ticketya-compra-v1-' + owner + '-' + eventId, owner, eventId,
      async (path, options) => (await api(path, options)).data);
  } catch (cause) { lastError = cause instanceof Error ? cause.message : 'No pudimos recuperar el intento local.'; }
  function refresh() {
    const attempt = flow?.current;
    create.disabled = resumeId !== null || busy || !flow || !!attempt || !getDraft() || getSelectedProfileId() !== owner;
    recover.hidden = !attempt; recover.disabled = busy;
    reset.hidden = !attempt || !(attempt.conflict && !attempt.purchaseId || ['EXPIRADA','RECHAZADA','CANCELADA'].includes(attempt.purchaseState || '') || attempt.purchaseState === 'APROBADA' && attempt.reservationState === 'CONFIRMADA' || ['VENCIDA','LIBERADA','DEVUELTA'].includes(attempt.reservationState || ''));
    reset.disabled = busy;
    if (attempt?.paymentUncertain || resumeId !== null) reset.hidden = true;
    approve.disabled = resumeId !== null && (!resumeVerified || attempt?.reservationState !== 'ACTIVA') || busy || attempt?.purchaseState !== 'PENDIENTE' || !attempt?.purchaseId || attempt.paymentDecision === false || getSelectedProfileId() !== owner;
    reject.disabled = resumeId !== null && (!resumeVerified || attempt?.reservationState !== 'ACTIVA') || busy || attempt?.purchaseState !== 'PENDIENTE' || !attempt?.purchaseId || attempt.paymentDecision === true || getSelectedProfileId() !== owner;
    paymentState.textContent = 'Estado de compra recibido: ' + (attempt?.purchaseState || 'sin consultar') + (attempt?.paymentUncertain ? ' · Resultado del pago incierto: consultá la compra o reintentá únicamente la misma decisión.' : '')
      + (attempt?.purchaseState === 'EXPIRADA' ? ' · La compra expiró y no emite entradas, aun con HTTP 200.' : attempt?.purchaseState === 'RECHAZADA' ? ' · Pago simulado rechazado; no se emiten entradas.' : attempt?.purchaseState === 'APROBADA' ? ' · Pago simulado aprobado.' : '');
    if (busy) status.textContent = 'Consultando el intento guardado. No cambies de perfil hasta conocer el resultado.';
    else status.textContent = lastError || (attempt ? 'Intento conservado · sector #' + attempt.body.idSector + ' · ' + (attempt.body.idsAsientos ? 'asientos #' + attempt.body.idsAsientos.join(', #') : attempt.body.cantidadEntradas + ' entradas CAMPO') + (attempt.purchaseId ? ' · compra #' + attempt.purchaseId + ' · ' + attempt.purchaseState : ' · resultado pendiente de confirmar. Recuperá usando la misma clave y selección.') : 'Todavía no se creó una compra.');
    reservation.textContent = attempt?.reservationId ? 'Reserva #' + attempt.reservationId + ' · último estado consultado: ' + (attempt.reservationState || 'sin verificar') + (attempt.expiresAt ? ' · vence: ' + new Date(attempt.expiresAt).toLocaleString('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' }) + ' (Buenos Aires)' : '') : '';
    tick();
  }
  function tick() {
    const attempt = flow?.current;
    if (!attempt?.expiresAt || attempt.reservationState !== 'ACTIVA') { clock.textContent = ''; return; }
    const seconds = Math.max(0, Math.ceil((Date.parse(attempt.expiresAt) - Date.now()) / 1000));
    clock.textContent = seconds ? 'Tiempo orientativo: ' + Math.floor(seconds / 60) + ':' + String(seconds % 60).padStart(2,'0') + '. El servidor determina el estado.' : 'Se alcanzó el vencimiento según el reloj local. Consultá el estado; esto no cancela ni renueva la compra.';
  }
  async function submit(start: boolean, payment?: boolean) {
    if (busy || !flow || owner === null || getSelectedProfileId() !== owner) return;
    busy = true; lastError = '';
    try {
      if (start) {
        const draft = getDraft(); if (!draft) throw new Error('Revisá la selección antes de crear una compra.');
        flow.start(purchaseSelection(draft), crypto.randomUUID());
      }
      document.querySelector<HTMLDialogElement>('#review-dialog')!.close();
      document.querySelector('#purchase-panel')!.scrollIntoView({ block: 'center' });
      refresh();
      await flow.recover(payment);
      resumeVerified = true;
    } catch (cause) {
      if (resumeId !== null) resumeVerified = false;
      lastError = cause instanceof ApiError && cause.status === 409
        ? 'Conflicto: ' + cause.message + '. Conservamos el intento. Revisá la disponibilidad actual antes de preparar otra selección.'
        : (cause instanceof Error ? cause.message : 'No se pudo leer el resultado.') + ' No afirmamos que la compra haya fallado: recuperá el intento conservado.';
      if (cause instanceof ApiError && cause.status === 409) conflict();
    } finally {
      if (flow.current?.purchaseState === 'APROBADA' && getSelectedProfileId() === owner) {
        entriesStatus.textContent = 'Consultando entradas de la compra aprobada…'; entriesList.replaceChildren();
        try {
          const entries = await flow.entries();
          if (getSelectedProfileId() === owner) {
            for (const entry of entries) { const li = document.createElement('li'); li.textContent = 'Entrada #' + entry.id + ' · ' + entry.estadoEntrada + ' · QR: ' + entry.estadoQr; entriesList.append(li); }
            const link = document.createElement('a'); link.href = '/perfil/?compra=' + flow.current!.purchaseId + '#entradas'; link.textContent = 'Ver entradas de esta compra en Mi cuenta'; entriesList.append(link);
            entriesStatus.textContent = entries.length ? 'Entradas emitidas: ' + entries.length : 'Compra APROBADA, pero todavía no se recibieron entradas. Volvé a consultar; no repitas el pago.';
          }
        } catch (cause) { entriesStatus.textContent = 'Compra aprobada; no pudimos consultar las entradas. Recuperá el estado sin volver a pagar. ' + (cause instanceof Error ? cause.message : ''); }
      } else { entriesList.replaceChildren(); entriesStatus.textContent = 'Las entradas se consultan únicamente si la compra está APROBADA.'; }
      busy = false; refresh();
    }
  }
  approve.addEventListener('click', () => { if (!approve.disabled) submit(false, true); });
  reject.addEventListener('click', () => { if (!reject.disabled) submit(false, false); });
  create.addEventListener('click', () => submit(true));
  recover.addEventListener('click', () => submit(false));
  reset.addEventListener('click', () => {
    if (busy || !flow || getSelectedProfileId() !== owner) return;
    try { flow.reset(); entriesList.replaceChildren(); entriesStatus.textContent = ''; lastError = 'Intento anterior cerrado. Revisá disponibilidad y selección antes de crear otra compra.'; conflict(); }
    catch (cause) { lastError = cause instanceof Error ? cause.message : 'No se pudo cerrar el intento.'; }
    refresh();
  });
  if (resumeId !== null && owner !== null) {
    busy = true;
    (async () => {
      try {
        if (!Number.isSafeInteger(resumeId) || resumeId <= 0) throw new Error('ID de compra inválido.');
        const purchase = parsePurchase((await api('/compras/' + resumeId, { cache: 'no-store' })).data, owner);
        if (purchase.id !== resumeId || purchase.idEvento !== eventId || getSelectedProfileId() !== owner) throw new Error('La compra no corresponde al perfil o evento seleccionado.');
        const key = seedResume(localStorage, purchase);
        flow = purchaseFlow(localStorage, key, owner, eventId, async (path, options) => (await api(path, options)).data);
        busy = false; await submit(false);
      } catch (cause) { lastError = cause instanceof Error ? cause.message : 'No pudimos retomar la compra.'; }
      finally { busy = false; refresh(); }
    })();
  }
  setInterval(tick, 1000);
  refresh();
  return { refresh };
}
