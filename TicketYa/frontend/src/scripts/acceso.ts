import { api, ApiError } from './api';
import { accessDenial, accessRequest, parseAccessEntry, type AccessEntry } from './acceso-data';
import { getSession } from './session';

const panel = document.querySelector<HTMLElement>('#access-panel')!;
const permission = document.querySelector<HTMLElement>('#access-permission')!;
const form = document.querySelector<HTMLFormElement>('#access-form')!;
const eventInput = document.querySelector<HTMLInputElement>('#access-event')!;
const qrInput = document.querySelector<HTMLInputElement>('#access-qr')!;
const lookup = document.querySelector<HTMLButtonElement>('#access-lookup')!;
const confirm = document.querySelector<HTMLButtonElement>('#access-confirm')!;
const next = document.querySelector<HTMLButtonElement>('#access-next')!;
const status = document.querySelector<HTMLElement>('#access-status')!;
const entryInfo = document.querySelector<HTMLElement>('#access-entry')!;
const result = document.querySelector<HTMLElement>('#access-result');
const verdict = document.querySelector<HTMLElement>('#access-verdict');
const history = document.querySelector<HTMLOListElement>('#access-history');
const emptyHistory = document.querySelector<HTMLElement>('#access-history-empty');

let busy = false;
let verified: { entry: AccessEntry; request: ReturnType<typeof accessRequest>; token: string } | null = null;

function allowedSession() {
  const session = getSession();
  return session?.roles.some(role => role === 'PERSONAL_ACCESO' || role === 'ADMINISTRADOR') ? session : null;
}

function refresh() {
  eventInput.disabled = qrInput.disabled = lookup.disabled = next.disabled = busy;
  confirm.disabled = busy || verified === null;
  form.setAttribute('aria-busy', String(busy));
}

function clearEntry() {
  verified = null;
  status.textContent = '';
  entryInfo.textContent = '';
  entryInfo.hidden = true;
  if (result) delete result.dataset.tone;
  if (verdict) verdict.textContent = '';
  refresh();
}

function guard() {
  const allowed = allowedSession();
  panel.hidden = !allowed;
  permission.hidden = !!allowed;
  if (!allowed) {
    permission.textContent = 'Esta pantalla requiere el rol de personal de acceso o administrador.';
    if (!getSession()) location.replace('/login/?volver=' + encodeURIComponent('/acceso/'));
  }
}

function messageFor(cause: unknown, marking = false) {
  if (cause instanceof ApiError && cause.status === 404) return 'No encontramos una entrada con ese QR.';
  const message = cause instanceof Error ? cause.message : 'No pudimos completar la operación.';
  return marking ? message + ' Consultá el QR antes de intentar registrar el ingreso otra vez.' : message;
}

function showEntry(entry: AccessEntry) {
  entryInfo.textContent = 'Entrada #' + entry.id + ' · Evento #' + entry.idEvento + ' · Sector #' + entry.idSector;
  if (Number.isSafeInteger(entry.idCliente)) entryInfo.textContent += ' · Titular #' + entry.idCliente;
  entryInfo.hidden = false;
}

function showResult(label: string, detail: string, valid: boolean, qr?: string) {
  if (result) result.dataset.tone = valid ? 'valid' : 'invalid';
  if (verdict) verdict.textContent = label;
  status.textContent = detail;
  if (!history || !qr) return;
  const item = document.createElement('li');
  item.textContent = new Date().toLocaleTimeString('es-AR', { hour: '2-digit', minute: '2-digit', timeZone: 'America/Argentina/Buenos_Aires' }) + ' · QR …' + qr.slice(-6) + ' · ' + label + ' · ' + detail;
  history.prepend(item);
  if (history.children.length > 8) history.lastElementChild?.remove();
  if (emptyHistory) emptyHistory.hidden = true;
}

form.addEventListener('submit', async event => {
  event.preventDefault();
  const session = allowedSession();
  if (!session) { guard(); return; }
  if (busy) return;
  clearEntry();
  try {
    const request = accessRequest(eventInput.valueAsNumber, qrInput.value);
    busy = true;
    refresh();
    status.textContent = 'Consultando la entrada…';
    const { data } = await api(request.path, { cache: 'no-store' });
    if (allowedSession()?.accessToken !== session.accessToken) return;
    const entry = parseAccessEntry(data, request.qr);
    showEntry(entry);
    const denial = accessDenial(entry, request.eventId);
    if (!denial) verified = { entry, request, token: session.accessToken };
    const label = !denial ? 'Válido' : entry.estadoQr === 'ESCANEADO' && entry.idEvento === request.eventId
      ? 'Ya utilizado' : entry.estadoEntrada.startsWith('DEVOLUCION_') && entry.idEvento === request.eventId ? 'Rechazado / devuelto' : 'Inválido';
    showResult(label, denial ?? 'Entrada disponible. Confirmá el ingreso cuando la persona acceda.', !denial, request.qr);
  } catch (cause) {
    if (allowedSession()?.accessToken === session.accessToken) showResult('No validado', messageFor(cause), false, busy ? qrInput.value : undefined);
  } finally {
    busy = false;
    refresh();
  }
});

confirm.addEventListener('click', async () => {
  const current = verified;
  if (busy || !current) return;
  if (allowedSession()?.accessToken !== current.token) { clearEntry(); guard(); return; }
  verified = null;
  busy = true;
  refresh();
  status.textContent = 'Registrando el ingreso…';
  try {
    const { data } = await api(current.request.markPath, { method: 'PATCH' });
    if (allowedSession()?.accessToken !== current.token) return;
    const entry = parseAccessEntry(data, current.request.qr);
    if (entry.id !== current.entry.id || entry.idEvento !== current.request.eventId || entry.estadoQr !== 'ESCANEADO') {
      throw new Error('El servidor devolvió un estado de ingreso que no pudimos confirmar.');
    }
    showEntry(entry);
    showResult('Ingreso registrado', 'Ingreso registrado.', true, current.request.qr);
  } catch (cause) {
    if (allowedSession()?.accessToken === current.token) showResult('Ingreso no confirmado', messageFor(cause, true), false, current.request.qr);
  } finally {
    busy = false;
    refresh();
  }
});

form.addEventListener('input', clearEntry);
next.addEventListener('click', () => { qrInput.value = ''; clearEntry(); qrInput.focus(); });
window.addEventListener('ticketya:profile', () => {
  qrInput.value = ''; history?.replaceChildren();
  if (emptyHistory) emptyHistory.hidden = false;
  clearEntry(); guard();
});
window.addEventListener('pageshow', event => {
  if (event.persisted) { qrInput.value = ''; clearEntry(); guard(); }
});
guard();
if (!panel.hidden && !document.activeElement?.matches('input, button, a')) qrInput.focus();
