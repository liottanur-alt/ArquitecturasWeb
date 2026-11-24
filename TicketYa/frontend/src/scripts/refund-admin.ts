import { api } from './api';
import { parseEntry, type Entry } from './entry-data';
import { parseRefunds, type RefundRequest } from './refund-data';
import { getSession } from './session';

const panel = document.querySelector<HTMLElement>('#refund-admin')!;
const access = document.querySelector<HTMLElement>('#refund-admin-access')!;
const login = document.querySelector<HTMLAnchorElement>('#refund-admin-login')!;
const refresh = document.querySelector<HTMLButtonElement>('#refund-admin-refresh')!;
const list = document.querySelector<HTMLUListElement>('#refund-admin-list')!;
const listStatus = document.querySelector<HTMLElement>('#refund-admin-list-status')!;
const detail = document.querySelector<HTMLDListElement>('#refund-admin-detail')!;
const detailStatus = document.querySelector<HTMLElement>('#refund-admin-detail-status')!;
const recheck = document.querySelector<HTMLButtonElement>('#refund-admin-recheck')!;
const form = document.querySelector<HTMLFormElement>('#refund-admin-resolution')!;
const decision = document.querySelector<HTMLSelectElement>('#refund-admin-decision')!;
const submit = document.querySelector<HTMLButtonElement>('#refund-admin-submit')!;
const result = document.querySelector<HTMLElement>('#refund-admin-result')!;

type Detail = { request: RefundRequest; entry: Entry };
const dateFormat = new Intl.DateTimeFormat('es-AR', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Argentina/Buenos_Aires' });
const getAdmin = () => {
  const session = getSession();
  return session?.roles.includes('ADMINISTRADOR') ? session : null;
};
const message = (cause: unknown, fallback: string) => cause instanceof Error ? cause.message : fallback;
let activeToken: string | undefined;
let sessionSequence = 0;
let listSequence = 0;
let detailSequence = 0;
let selectedId: number | null = null;
let selected: Detail | null = null;
let resolving = false;

function parseRequest(value: unknown): RefundRequest {
  const clientId = (value as { idCliente?: unknown } | null)?.idCliente;
  if (typeof clientId !== 'number' || !Number.isSafeInteger(clientId) || clientId <= 0) {
    throw new Error('El servidor devolvió una solicitud sin un cliente válido.');
  }
  return parseRefunds([value], clientId)[0];
}

function active(token: string) {
  return token === activeToken && token === getAdmin()?.accessToken;
}

function renderDetail({ request, entry }: Detail) {
  const rows = [
    ['Solicitud', '#' + request.id + ' · ' + request.estadoSolicitud],
    ['Fecha de solicitud', dateFormat.format(Date.parse(request.fechaSolicitud))],
    ['Cliente y compra', 'Cliente #' + request.idCliente + ' · Compra #' + request.idCompra],
    ['Motivo', request.descripcion],
    ['Entrada', '#' + entry.id + ' · ' + entry.estadoEntrada],
    ['Evento y sector', 'Evento #' + entry.idEvento + ' · Sector #' + entry.idSector],
    ['Reintegro simulado', request.estadoReintegro ?? 'Sin reintegro registrado'],
  ];
  if (request.fechaResolucion && Number.isFinite(Date.parse(request.fechaResolucion))) {
    rows.push(['Fecha de resolución', dateFormat.format(Date.parse(request.fechaResolucion))]);
  }
  const fields = rows.flatMap(([label, value]) => {
    const term = document.createElement('dt');
    const definition = document.createElement('dd');
    term.textContent = label;
    definition.textContent = value;
    return [term, definition];
  });
  detail.replaceChildren(...fields);
  detail.hidden = false;
  form.hidden = request.estadoSolicitud !== 'PENDIENTE' || entry.estadoEntrada !== 'SOLICITUD_DEVOLUCION';
  detailStatus.textContent = '';
}

async function loadDetail(id: number, token: string) {
  if (!active(token)) return;
  const version = ++detailSequence;
  selectedId = id;
  list.querySelectorAll<HTMLButtonElement>('[data-request]').forEach(button =>
    button.setAttribute('aria-pressed', String(Number(button.dataset.request) === id)));
  selected = null;
  detail.hidden = true;
  form.hidden = true;
  recheck.hidden = false;
  detailStatus.textContent = 'Consultando solicitud y entrada…';
  const current = () => version === detailSequence && active(token);
  try {
    const request = parseRequest((await api('/solicitudes/' + id, { cache: 'no-store' })).data);
    if (!current()) return;
    if (request.id !== id) throw new Error('La respuesta no coincide con la solicitud elegida.');
    const entry = parseEntry((await api('/entradas/' + request.idEntrada, { cache: 'no-store' })).data, request.idCliente);
    if (!current()) return;
    if (entry.id !== request.idEntrada) throw new Error('La entrada consultada no coincide con la solicitud.');
    selected = { request, entry };
    renderDetail(selected);
  } catch (cause) {
    if (current()) detailStatus.textContent = message(cause, 'No se pudo consultar el detalle.');
  }
}

async function loadPending(token: string) {
  if (!active(token)) return;
  const version = ++listSequence;
  refresh.disabled = true;
  listStatus.textContent = 'Consultando solicitudes pendientes…';
  const current = () => version === listSequence && active(token);
  try {
    const { data } = await api('/solicitudes/pendientes', { cache: 'no-store' });
    if (!current()) return;
    if (!Array.isArray(data)) throw new Error('El servidor devolvió una lista inválida.');
    const requests = data.map(parseRequest).filter(request => request.estadoSolicitud === 'PENDIENTE');
    const items = requests.map(request => {
      const item = document.createElement('li');
      const view = document.createElement('button');
      view.type = 'button';
      view.className = 'ops-button ops-secondary refund-request';
      view.dataset.request = String(request.id);
      view.setAttribute('aria-pressed', String(request.id === selectedId));
      const name = document.createElement('strong');
      name.textContent = 'Solicitud #' + request.id;
      const context = document.createElement('small');
      context.textContent = 'Entrada #' + request.idEntrada + ' · ' + dateFormat.format(Date.parse(request.fechaSolicitud));
      view.append(name, context);
      view.addEventListener('click', () => {
        if (resolving || !active(token)) return;
        decision.value = '';
        result.textContent = '';
        void loadDetail(request.id, token);
      });
      item.append(view);
      return item;
    });
    list.replaceChildren(...items);
    listStatus.textContent = requests.length === 0 ? 'No hay solicitudes pendientes.' : '';
  } catch (cause) {
    if (current()) {
      list.replaceChildren();
      listStatus.textContent = message(cause, 'No se pudieron consultar las solicitudes.');
    }
  } finally {
    if (current()) refresh.disabled = false;
  }
}

const confirmation = document.querySelector<HTMLDialogElement>('#admin-confirm');
let confirmed: { id: number; state: string } | undefined;
confirmation?.addEventListener('close', () => {
  if (confirmation.returnValue === 'confirm' && confirmed) form.requestSubmit();
  confirmed = undefined;
});
form.addEventListener('submit', async event => {
  event.preventDefault();
  const admin = getAdmin();
  const state = decision.value;
  if (!admin || !active(admin.accessToken) || !selected || resolving || form.hidden
    || (state !== 'ACEPTADA' && state !== 'RECHAZADA')) return;
  const id = selected.request.id;
  if (confirmation && (confirmed?.id !== id || confirmed.state !== state || confirmation.returnValue !== 'confirm')) {
    confirmed = { id, state };
    confirmation.returnValue = 'cancel';
    document.querySelector('#admin-confirm-detail')!.textContent = state === 'ACEPTADA'
      ? 'La solicitud #' + id + ' iniciará un reintegro simulado y liberará el cupo.'
      : 'La solicitud #' + id + ' quedará rechazada.';
    confirmation.showModal();
    return;
  }
  const token = admin.accessToken;
  const version = sessionSequence;
  const current = () => version === sessionSequence && active(token);
  resolving = true;
  submit.disabled = decision.disabled = recheck.disabled = true;
  result.textContent = 'Enviando resolución…';
  let outcome: string;
  try {
    const { data } = await api('/solicitudes/' + id + '/resolver', {
      method: 'PUT',
      body: { idAdministrador: admin.userId, estadoSolicitud: state },
    });
    const updated = parseRequest(data);
    if (updated.id !== id || updated.estadoSolicitud !== state) throw new Error('La respuesta no confirmó la resolución solicitada.');
    outcome = 'El Gateway confirmó la resolución ' + state + '.';
  } catch (cause) {
    outcome = message(cause, 'No se pudo confirmar la resolución.') + ' Revisá los estados actualizados antes de volver a resolver.';
  }
  if (!current()) return;
  result.textContent = outcome;
  await Promise.all([loadDetail(id, token), loadPending(token)]);
  if (!current()) return;
  decision.value = '';
  resolving = false;
  submit.disabled = decision.disabled = recheck.disabled = false;
});

function syncSession() {
  const session = getSession();
  const token = session?.roles.includes('ADMINISTRADOR') ? session.accessToken : undefined;
  login.hidden = !!session;
  access.textContent = token ? '' : session ? 'Esta pantalla requiere una cuenta administradora.' : 'Iniciá sesión para administrar devoluciones.';
  if (token === activeToken) return;
  panel.hidden = !token;
  confirmed = undefined;
  if (confirmation?.open) confirmation.close();
  activeToken = token;
  sessionSequence++;
  detailSequence++;
  selectedId = selected = null;
  resolving = false;
  submit.disabled = decision.disabled = recheck.disabled = false;
  decision.value = '';
  detail.hidden = form.hidden = recheck.hidden = true;
  detail.replaceChildren();
  list.replaceChildren();
  detailStatus.textContent = 'Elegí una solicitud pendiente para consultar su entrada.';
  result.textContent = '';
  if (token) void loadPending(token);
}

refresh.addEventListener('click', () => { if (activeToken) void loadPending(activeToken); });
recheck.addEventListener('click', () => { if (selectedId !== null && activeToken && !resolving) void loadDetail(selectedId, activeToken); });
window.addEventListener('ticketya:profile', syncSession);
window.addEventListener('pageshow', syncSession);
document.addEventListener('visibilitychange', () => { if (!document.hidden) syncSession(); });
syncSession();
