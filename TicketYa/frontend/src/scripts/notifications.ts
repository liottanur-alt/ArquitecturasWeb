import { api } from './api';
import { getSession } from './session';

type Notice = { id: number; idCliente: number; asunto: string; mensaje: string; fechaCreacion: string; fechaLectura: string | null };
type Inbox = { notificaciones: Notice[]; noLeidas: number };
const endpoint = '/api/notificaciones';
const relative = new Intl.RelativeTimeFormat('es-AR', { numeric: 'auto' });

function relativeDate(date: string) {
  const minutes = Math.round((Date.parse(date) - Date.now()) / 60_000);
  if (Math.abs(minutes) < 60) return relative.format(minutes, 'minute');
  const hours = Math.round(minutes / 60);
  return Math.abs(hours) < 24 ? relative.format(hours, 'hour') : relative.format(Math.round(hours / 24), 'day');
}

document.querySelectorAll<HTMLElement>('[data-inbox]').forEach(root => {
  const trigger = root.querySelector<HTMLButtonElement>('.inbox-trigger');
  const dialog = root.querySelector<HTMLDialogElement>('dialog');
  const surface = dialog ?? root;
  const count = root.querySelector<HTMLElement>('.inbox-count');
  const status = root.querySelector<HTMLElement>('.inbox-status')!;
  const all = root.querySelector<HTMLButtonElement>('.inbox-read-all')!;
  const retry = root.querySelector<HTMLButtonElement>('.inbox-retry')!;
  const list = root.querySelector<HTMLOListElement>('ol')!;
  let token: string | undefined;
  let account: number | undefined;
  let sequence = 0;
  let writing = false;
  let unread = 0;
  let displayedUnread = 0;
  let inbox: Inbox | null = null;
  let filter = 'all';

  function badge() {
    if (count) {
      count.hidden = unread === 0;
      count.textContent = unread > 99 ? '99+' : String(unread);
      if (unread > displayedUnread) count.setAttribute('data-pulse', '');
      displayedUnread = unread;
    }
    trigger?.setAttribute('aria-label', unread ? `Notificaciones, ${unread} sin leer` : 'Notificaciones');
    all.disabled = writing || unread === 0;
  }

  function paint(data: Inbox | null) {
    if (!data || !Number.isSafeInteger(data.noLeidas) || data.noLeidas < 0 || !Array.isArray(data.notificaciones)
      || !data.notificaciones.every(n => n && n.idCliente === account && Number.isSafeInteger(n.id)
        && n.id > 0 && typeof n.asunto === 'string' && typeof n.mensaje === 'string'
        && Number.isFinite(Date.parse(n.fechaCreacion)) && (n.fechaLectura === null || Number.isFinite(Date.parse(n.fechaLectura))))) {
      throw new Error('La respuesta de notificaciones no se pudo verificar.');
    }
    unread = data.noLeidas;
    inbox = data;
    badge();
    const notices = data.notificaciones.filter(notice => filter === 'all' || (notice.fechaLectura === null) === (filter === 'unread'))
      .sort((a, b) => Date.parse(b.fechaCreacion) - Date.parse(a.fechaCreacion) || b.id - a.id);
    list.replaceChildren(...notices.map(notice => {
      const item = document.createElement('li');
      const title = document.createElement('strong');
      title.textContent = notice.asunto;
      const detail = document.createElement('p');
      detail.textContent = notice.mensaje;
      const time = document.createElement('time');
      time.dateTime = notice.fechaCreacion;
      time.title = new Date(notice.fechaCreacion).toLocaleString('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' });
      time.textContent = relativeDate(notice.fechaCreacion);
      item.append(title, detail, time);
      if (notice.fechaLectura === null) {
        item.dataset.unread = '';
        const mark = document.createElement('button');
        mark.type = 'button';
        mark.className = 'text-link';
        mark.textContent = 'Marcar como leída';
        mark.setAttribute('aria-label', 'Marcar como leída: ' + notice.asunto);
        mark.addEventListener('click', () => void write(endpoint + '/' + notice.id + '/lectura'));
        item.append(mark);
      }
      return item;
    }));
    status.textContent = notices.length ? '' : filter === 'read' ? 'Todavía no hay notificaciones leídas.' : 'No tenés notificaciones nuevas';
  }

  async function load() {
    if (!token) return;
    const version = ++sequence;
    retry.hidden = true;
    status.textContent = 'Cargando notificaciones…';
    surface.setAttribute('aria-busy', 'true');
    try {
      const { data } = await api<Inbox>(endpoint, { cache: 'no-store' });
      if (version === sequence) paint(data);
    } catch (error) {
      if (version !== sequence) return;
      status.textContent = error instanceof Error ? error.message : 'No pudimos cargar las notificaciones.';
      retry.hidden = false;
    } finally {
      if (version === sequence) surface.removeAttribute('aria-busy');
    }
  }

  async function write(path: string) {
    if (writing || !token) return;
    const session = token;
    writing = true;
    badge();
    try {
      await api(path, { method: 'PATCH' });
      if (token === session) window.dispatchEvent(new Event('ticketya:inbox-updated'));
    } catch (error) {
      if (token === session) status.textContent = error instanceof Error ? error.message : 'No se pudo actualizar la lectura.';
    } finally {
      if (token === session) { writing = false; badge(); }
    }
  }

  function sync() {
    const session = getSession();
    root.hidden = !session;
    if (session?.accessToken === token) return;
    token = session?.accessToken;
    account = session?.userId;
    sequence++;
    writing = false;
    unread = 0;
    inbox = null;
    surface.removeAttribute('aria-busy');
    list.replaceChildren();
    status.textContent = '';
    badge();
    if (dialog?.open) dialog.close();
    if (token) void load();
  }

  trigger?.addEventListener('click', () => { dialog?.showModal(); void load(); });
  count?.addEventListener('animationend', () => count.removeAttribute('data-pulse'));
  root.querySelector('.inbox-close')?.addEventListener('click', () => dialog?.close());
  dialog?.addEventListener('click', event => {
    if (event.target !== dialog) return;
    const bounds = dialog.getBoundingClientRect();
    if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) dialog.close();
  });
  const filters = root.querySelectorAll<HTMLButtonElement>('[data-inbox-filter]');
  filters.forEach(button => button.addEventListener('click', () => {
    filter = button.dataset.inboxFilter ?? 'all';
    filters.forEach(control => control.setAttribute('aria-pressed', String(control === button)));
    if (inbox) paint(inbox);
  }));
  all.addEventListener('click', () => void write(endpoint + '/lectura'));
  retry.addEventListener('click', () => void load());
  window.addEventListener('ticketya:profile', sync);
  window.addEventListener('ticketya:inbox-updated', () => void load());
  window.addEventListener('pageshow', sync);
  document.addEventListener('visibilitychange', () => { sync(); if (!document.hidden && !writing) void load(); });
  window.setInterval(() => {
    sync();
    if (!document.hidden && !dialog?.open && !writing) void load();
  }, 60_000);
  sync();
});
