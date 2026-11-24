import { api, ApiError } from './api';
import { parseEntry, type Entry } from './entry-data';
import { getAuthenticatedUserId } from './session';
import { parseRefunds, refundRule, type RefundRequest } from './refund-data';
export function initRefunds(owner: number, updateEntries: () => void) {
  const status = document.querySelector<HTMLElement>('#refunds-status')!;
  const dialog = document.querySelector<HTMLDialogElement>('#ticket-refund-dialog')!;
  const form = document.querySelector<HTMLFormElement>('#refund-form')!;
  const description = document.querySelector<HTMLTextAreaElement>('#refund-description')!;
  const submit = document.querySelector<HTMLButtonElement>('#confirm-refund')!;
  const message = document.querySelector<HTMLElement>('#refund-message')!;
  const list = document.querySelector<HTMLElement>('#refund-list')!;
  let requests: RefundRequest[] = [], ready = false, busy = false, version = 0;
  let selected: Entry | null = null, selectedEvent: unknown = null;
  const key = (id: number) => 'ticketya-refund-uncertain-' + owner + '-' + id;
  const currentOwner = () => getAuthenticatedUserId() === owner;
  function unresolved(id: number) {
    const raw = localStorage.getItem(key(id)); if (!raw) return false;
    const baseline = JSON.parse(raw) as number[];
    if (!Array.isArray(baseline) || !baseline.every(Number.isSafeInteger)) throw new Error('No se pudo verificar el intento local de devolución.');
    if (requests.some(r => r.idEntrada === id && !baseline.includes(r.id))) { localStorage.removeItem(key(id)); return false; }
    return true;
  }
  function paint() {
    if (!currentOwner()) return;
    list.replaceChildren();
    for (const r of requests) { const item = document.createElement('li'); item.textContent = 'Solicitud #' + r.id + ' · Entrada #' + r.idEntrada + ' · ' + r.estadoSolicitud + ' · ' + r.descripcion + (r.estadoReintegro ? ' · reintegro simulado: ' + r.estadoReintegro : '') + (r.estadoSolicitud === 'PENDIENTE' ? ' · pendiente de resolución; sin reintegro confirmado.' : r.estadoSolicitud === 'RECHAZADA' ? ' · El backend no permite ingreso por QR tras este rechazo.' : ''); list.append(item); }
    status.textContent = requests.length ? 'Solicitudes del perfil actualizadas desde el servidor.' : 'Este perfil no tiene solicitudes de devolución.';
  }
  async function load() {
    if (!currentOwner()) return;
    const token = ++version; ready = false; status.textContent = 'Consultando solicitudes de devolución…';
    try { const data = parseRefunds((await api('/solicitudes/cliente/' + owner, { cache: 'no-store' })).data, owner); if (token !== version || !currentOwner()) return; requests = data; ready = true; paint(); }
    catch (cause) { if (token === version && currentOwner()) { status.textContent = cause instanceof Error ? cause.message : 'No pudimos consultar las solicitudes.'; list.replaceChildren(); } }
  }
  async function open(entry: Entry) {
    if (busy || !currentOwner()) return;
    message.setAttribute('role', 'status');
    selected = null; description.value = ''; submit.disabled = true; message.textContent = 'Verificando entrada, plazo y solicitudes…'; dialog.showModal();
    const token = ++version;
    try {
      const [response, history] = await Promise.all([api('/entradas/' + entry.id, { cache: 'no-store' }), api('/solicitudes/cliente/' + owner, { cache: 'no-store' })]);
      if (token !== version || !currentOwner() || !dialog.open) return;
      const fresh = parseEntry(response.data, owner); if (fresh.id !== entry.id) throw new Error('La entrada consultada no coincide.');
      requests = parseRefunds(history.data, owner); ready = true; paint();
      let event: unknown = null;
      try { event = (await api('/api/eventos/' + fresh.idEvento, { cache: 'no-store' })).data; } catch {}
      if (token !== version || !currentOwner() || !dialog.open) return;
      if (event && (event as { id?: number }).id !== fresh.idEvento) event = null;
      selected = fresh; selectedEvent = event;
      const rule = refundRule(fresh, requests, event);
      document.querySelector('#refund-title')!.textContent = 'Devolución de la entrada #' + fresh.id;
      message.textContent = rule.message;
      if (unresolved(fresh.id)) { message.textContent += ' Hay un envío con resultado incierto. Consultá solicitudes antes de volver a enviar; no se repetirá automáticamente.'; return; }
      const start = (event as { fechaHora?: string } | null)?.fechaHora;
      if (!start || !Number.isFinite(Date.parse(start + '-03:00'))) {
        message.textContent = 'No pudimos confirmar la fecha del evento. Volvé a consultar antes de solicitar la devolución.';
        return;
      }
      submit.disabled = !rule.allowed;
    } catch (cause) { if (token === version && currentOwner() && dialog.open) message.textContent = cause instanceof Error ? cause.message : 'No pudimos verificar la entrada.'; }
  }
  form.addEventListener('submit', async event => {
    event.preventDefault(); if (busy || !selected || submit.disabled || !ready || !currentOwner()) return;
    const reason = description.value.trim(); if (!reason) { message.textContent = 'Ingresá una descripción del motivo.'; description.focus(); return; }
    const rule = refundRule(selected, requests, selectedEvent); if (!rule.allowed) { message.textContent = rule.message; submit.disabled = true; return; }
    const entryId = selected.id; busy = true; submit.disabled = true; form.setAttribute('aria-busy','true');
    let sent = false, confirmed = false;
    try {
      if (unresolved(entryId)) throw new Error('Existe un envío incierto. Consultá solicitudes antes de volver a enviar.');
      localStorage.setItem(key(entryId), JSON.stringify(requests.filter(r => r.idEntrada === entryId).map(r => r.id)));
      message.textContent = 'Enviando solicitud al servidor…'; sent = true;
      const response = await api('/solicitudes', { method: 'POST', body: { idEntrada: entryId, idCliente: owner, descripcion: reason } });
      const result = parseRefunds([response.data], owner)[0]; if (result.idEntrada !== entryId) throw new Error('No se pudo verificar la solicitud recibida.');
      confirmed = true; localStorage.removeItem(key(entryId));
      if (currentOwner()) { requests = [...requests.filter(r => r.id !== result.id), result]; paint(); message.textContent = 'Solicitud #' + result.id + ' · ' + result.estadoSolicitud + '. Crear la solicitud no confirma un reintegro.'; }
    } catch (cause) {
      message.setAttribute('role', 'alert');
      if (cause instanceof ApiError && [400,403,404,409].includes(cause.status)) { localStorage.removeItem(key(entryId)); message.textContent = cause.message + ' · Se consultarán los estados actuales; no se confirmó ninguna aceptación.'; }
      else message.textContent = confirmed ? 'Solicitud recibida, pero falló su actualización local. Consultá las solicitudes.' : (cause instanceof Error ? cause.message : 'No se pudo leer el resultado.') + (sent ? ' Resultado incierto: consultá las solicitudes antes de volver a enviar.' : ' No se envió otra solicitud.');
    } finally {
      busy = false; form.removeAttribute('aria-busy');
      if (currentOwner()) { await load(); updateEntries(); }
      // Se requiere volver a abrir y verificar la entrada antes de otro envío.
      submit.disabled = true;
    }
  });
  document.querySelectorAll('[data-close-refund]').forEach(button => button.addEventListener('click', () => dialog.close()));
  dialog.addEventListener('close', () => { if (!busy) version++; selected = null; });
  document.querySelector('#refresh-refunds')!.addEventListener('click', async () => { if (!busy) { await load(); updateEntries(); } });
  window.addEventListener('pageshow', event => { if (event.persisted && !busy) load(); });
  document.addEventListener('visibilitychange', () => { if (!document.hidden && !busy && currentOwner()) load(); });
  load();
  return { open, describe(entry: Entry) { return requests.filter(r => r.idEntrada === entry.id).map(r => 'Solicitud #' + r.id + ': ' + r.estadoSolicitud).join(' · '); } };
}
