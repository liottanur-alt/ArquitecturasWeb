import { parsePurchase, seedResume } from './history-data';
import { api, ApiError } from './api';
import { purchaseFlow } from './purchase-attempt';
import { initQueue } from './purchase-queue';
import { purchaseSelection, type Draft } from './selection-data';
import { getAuthenticatedUserId } from './session';
export function initPurchase(owner: number | null, eventId: number, getDraft: () => Draft | null, conflict: () => void, requiresQueue?: () => boolean) {
  const create = document.querySelector<HTMLButtonElement>('#create-purchase')!;
  const recover = document.querySelector<HTMLButtonElement>('#recover-purchase')!;
  const reset = document.querySelector<HTMLButtonElement>('#new-purchase')!;
  const status = document.querySelector<HTMLElement>('#purchase-status')!;
  const approve = document.querySelector<HTMLButtonElement>('#approve-payment')!;
  const reject = document.querySelector<HTMLButtonElement>('#reject-payment')!;
  const paymentState = document.querySelector<HTMLElement>('#payment-state')!;
  const entriesStatus = document.querySelector<HTMLElement>('#payment-entries-status')!;
  const entriesList = document.querySelector<HTMLElement>('#payment-entries')!;
  const dialog = document.querySelector<HTMLDialogElement>('#review-dialog')!;
  const reviewActions = document.querySelector<HTMLFieldSetElement>('#review-actions');
  const panel = document.querySelector<HTMLElement>('#purchase-panel')!;
  const paymentControls = document.querySelector<HTMLElement>('#payment-controls');
  let flow: ReturnType<typeof purchaseFlow> | undefined, busy = false;
  let pageActive = true;
  let lastError = '';
  const resumeParam = new URLSearchParams(location.search).get('compra');
  const resumeId = resumeParam === null ? null : Number(resumeParam);
  const isOwner = () => owner !== null && getAuthenticatedUserId() === owner;
  const queue = requiresQueue ? initQueue(eventId,
    document.querySelector<HTMLElement>('#purchase-queue')!,
    document.querySelector<HTMLButtonElement>('#join-queue')!,
    document.querySelector<HTMLElement>('#queue-status')!,
    () => pageActive && document.visibilityState !== 'hidden' && dialog.open && isOwner() && resumeId === null
      && !busy && !!flow && !flow.current && requiresQueue(),
    refresh
  ) : undefined;
  const back = document.querySelector<HTMLElement>('#back-history'); if (back) back.hidden = resumeId === null;
  try {
    if (owner !== null && resumeId === null) flow = purchaseFlow(localStorage, 'ticketya-compra-v1-' + owner + '-' + eventId, owner, eventId,
      async (path, options) => (await api(path, options)).data);
  } catch (cause) { lastError = cause instanceof Error ? cause.message : 'No pudimos recuperar el intento local.'; }
  function refresh() {
    queue?.refresh();
    const authenticated = isOwner();
    const attempt = authenticated ? flow?.current : null;
    panel.hidden = !authenticated || (!attempt && !busy && resumeId === null && !lastError);
    panel.setAttribute('aria-busy', String(busy));
    create.setAttribute('aria-busy', String(busy));
    if (reviewActions) reviewActions.disabled = busy;
    if (paymentControls) paymentControls.hidden = attempt?.purchaseState !== 'PENDIENTE';
    create.disabled = !authenticated || resumeId !== null || busy || !flow || !!attempt || !getDraft()
      || (!!requiresQueue?.() && !queue?.authorization());
    recover.hidden = !authenticated || (!attempt && resumeId === null); recover.disabled = busy;
    reset.hidden = !authenticated || resumeId !== null || !flow?.canReset();
    approve.disabled = !authenticated || busy || !flow?.canPay(true);
    reject.disabled = !authenticated || busy || !flow?.canPay(false);
    const paymentMessages: Record<string, string> = {
      PENDIENTE: 'Reserva pendiente de pago.',
      EXPIRADA: 'La reserva venció y la compra expiró.',
      RECHAZADA: 'Pago simulado rechazado.',
      APROBADA: 'Pago simulado aprobado.',
    };
    paymentState.textContent = attempt?.paymentUncertain
      ? 'Resultado del pago incierto. Consultá la compra antes de reintentar.'
      : attempt?.purchaseState ? paymentMessages[attempt.purchaseState] ?? attempt.purchaseState : '';
    paymentState.dataset.state = attempt?.paymentUncertain ? 'uncertain' : attempt?.purchaseState ?? '';
    if (!authenticated) {
      status.textContent = 'Iniciá sesión para consultar tus compras.';
      entriesList.replaceChildren();
      entriesStatus.textContent = '';
    }
    else if (busy) status.textContent = 'Consultando la compra…';
    else if (lastError) status.textContent = lastError;
    else if (attempt?.purchaseId) {
      const count = attempt.body.idsAsientos?.length ?? attempt.body.cantidadEntradas;
      status.textContent = 'Compra #' + attempt.purchaseId + ' · ' + (count === 1 ? '1 entrada' : count + ' entradas');
    } else status.textContent = attempt ? 'Consultá el estado para recuperar tu selección.' : 'Todavía no se creó una compra.';
  }
  async function submit(start: boolean, payment?: boolean) {
    if (busy || !flow || !isOwner() || (start && create.disabled)) return;
    const authorization = start ? queue?.authorization() : undefined;
    if (start && requiresQueue?.() && !authorization) { refresh(); return; }
    busy = true; lastError = '';
    try {
      if (start) {
        const draft = getDraft(); if (!draft) throw new Error('Revisá la selección antes de crear una compra.');
        flow.start(purchaseSelection(draft), crypto.randomUUID(), authorization);
      }
      refresh();
      if (!start) {
        dialog.close();
        panel.scrollIntoView({ block: 'nearest' });
      }
      await flow.recover(payment);
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : 'No se pudo leer el resultado.';
      if (cause instanceof ApiError && cause.status === 409 && flow.canReset()) {
        lastError = message + ' Revisá la disponibilidad antes de preparar otra selección.';
        if (isOwner()) conflict();
      } else {
        lastError = message + ' Consultá el estado del intento conservado.';
      }
    } finally {
      if (flow.current?.purchaseState === 'APROBADA' && isOwner()) {
        entriesStatus.textContent = 'Consultando entradas de la compra aprobada…'; entriesList.replaceChildren();
        try {
          const entries = await flow.entries();
          if (isOwner()) {
            for (const entry of entries) { const li = document.createElement('li'); li.textContent = 'Entrada #' + entry.id + ' · ' + entry.estadoEntrada + ' · QR: ' + entry.estadoQr; entriesList.append(li); }
            const link = document.createElement('a');
            link.href = '/perfil/?compra=' + flow.current!.purchaseId + '#entradas';
            link.textContent = 'Ver entradas de esta compra en Mi cuenta';
            const action = document.createElement('li');
            action.append(link);
            entriesList.append(action);
            entriesStatus.textContent = entries.length ? 'Entradas emitidas: ' + entries.length : 'Compra APROBADA, pero todavía no se recibieron entradas. Volvé a consultar; no repitas el pago.';
          }
        } catch (cause) { entriesStatus.textContent = 'Compra aprobada; no pudimos consultar las entradas. Recuperá el estado sin volver a pagar. ' + (cause instanceof Error ? cause.message : ''); }
      } else { entriesList.replaceChildren(); entriesStatus.textContent = 'Las entradas se consultan únicamente si la compra está APROBADA.'; }
      busy = false; refresh();
      if (start && isOwner()) {
        if (dialog.open) dialog.close();
        panel.scrollIntoView({ block: 'nearest' });
      }
    }
  }
  approve.addEventListener('click', () => { if (!approve.disabled) submit(false, true); });
  reject.addEventListener('click', () => { if (!reject.disabled) submit(false, false); });
  create.addEventListener('click', () => submit(true));
  recover.addEventListener('click', () => flow ? submit(false) : resume());
  reset.addEventListener('click', () => {
    if (busy || !flow || !isOwner()) return;
    try { flow.reset(); entriesList.replaceChildren(); entriesStatus.textContent = ''; lastError = 'Intento anterior cerrado. Revisá disponibilidad y selección antes de crear otra compra.'; conflict(); }
    catch (cause) { lastError = cause instanceof Error ? cause.message : 'No se pudo cerrar el intento.'; }
    refresh();
  });
  async function resume() {
    if (busy || resumeId === null || owner === null || !isOwner()) return;
    busy = true;
    refresh();
    try {
      if (!Number.isSafeInteger(resumeId) || resumeId <= 0) throw new Error('ID de compra inválido.');
      const path = '/compras/' + resumeId;
      const purchase = parsePurchase((await api(path, { cache: 'no-store' })).data, owner);
      if (purchase.id !== resumeId || purchase.idEvento !== eventId || !isOwner()) throw new Error('La compra no corresponde a tu cuenta o al evento.');
      const key = seedResume(localStorage, purchase);
      let initialPurchase: typeof purchase | null = purchase;
      flow = purchaseFlow(localStorage, key, owner, eventId, async (requestPath, options) => {
        if (requestPath === path && initialPurchase) {
          const data = initialPurchase;
          initialPurchase = null;
          return data;
        }
        return (await api(requestPath, options)).data;
      });
      busy = false; await submit(false);
    } catch (cause) { lastError = cause instanceof Error ? cause.message : 'No pudimos retomar la compra.'; }
    finally { busy = false; refresh(); }
  }
  window.addEventListener('ticketya:profile', refresh);
  if (queue) {
    dialog.addEventListener('close', refresh);
    document.addEventListener('visibilitychange', refresh);
    window.addEventListener('pagehide', () => { pageActive = false; refresh(); });
    window.addEventListener('pageshow', () => { pageActive = true; refresh(); });
  }
  void resume();
  refresh();
  return { refresh };
}
