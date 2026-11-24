import { api } from './api';
import { getSession } from './session';

type Directory = 'refunds' | 'users' | 'events';
const nav = document.querySelector<HTMLElement>('#admin-nav')!;
const refunds = document.querySelector<HTMLElement>('#refund-admin')!;
const section = document.querySelector<HTMLElement>('#admin-directory')!;
const title = document.querySelector('#admin-directory-title')!;
const filter = document.querySelector<HTMLInputElement>('#admin-filter')!;
const status = document.querySelector('#admin-directory-status')!;
const head = document.querySelector('#admin-table-head')!;
const body = document.querySelector('#admin-table-body')!;
const previous = document.querySelector<HTMLButtonElement>('#admin-previous')!;
const next = document.querySelector<HTMLButtonElement>('#admin-next')!;
const buttons = [...nav.querySelectorAll<HTMLButtonElement>('[data-admin-view]')];
const pageSize = 10;
let view: Directory = 'refunds';
let rows: string[][] = [];
let page = 0;
let version = 0;
let token: string | undefined;

function row(tag: 'th' | 'td', values: string[]) {
  const tr = document.createElement('tr');
  for (const value of values) {
    const cell = document.createElement(tag);
    cell.textContent = value;
    if (tag === 'th') cell.scope = 'col';
    tr.append(cell);
  }
  return tr;
}

function paint() {
  const query = filter.value.trim().toLocaleLowerCase('es-AR');
  const matches = rows.filter(values => values.slice(0, 2).some(value => value.toLocaleLowerCase('es-AR').includes(query)));
  body.replaceChildren(...matches.slice(page * pageSize, (page + 1) * pageSize).map(values => row('td', values)));
  previous.disabled = page === 0;
  next.disabled = (page + 1) * pageSize >= matches.length;
  status.textContent = matches.length ? `Página ${page + 1} de ${Math.ceil(matches.length / pageSize)} · ${matches.length} resultados` : 'Sin resultados.';
}

async function choose(selected: Directory) {
  if (!token) return;
  const current = ++version;
  view = selected;
  buttons.forEach(button => button.setAttribute('aria-pressed', String(button.dataset.adminView === view)));
  refunds.hidden = view !== 'refunds';
  section.hidden = view === 'refunds';
  if (view === 'refunds') return;
  page = 0; rows = []; filter.value = ''; filter.disabled = true;
  previous.disabled = next.disabled = true;
  body.replaceChildren();
  const users = view === 'users';
  title.textContent = users ? 'Usuarios y roles' : 'Eventos publicados';
  head.replaceChildren(row('th', users ? ['ID', 'Nombre', 'Rol'] : ['ID', 'Evento', 'Estado']));
  status.textContent = 'Cargando datos…';
  try {
    const options = { cache: 'no-store' } as const;
    const [{ data }, roles] = await Promise.all([
      api<Record<string, unknown>[]>(users ? '/usuarios' : '/api/eventos', options),
      users ? api<{ id: number; name: string }[]>('/roles', options).then(response => response.data) : Promise.resolve([]),
    ]);
    if (version !== current) return;
    if (!Array.isArray(data) || !Array.isArray(roles)) throw new Error('El servidor devolvió datos inválidos.');
    rows = data.map(item => [String(item.id), String(item.nombre ?? 'Sin nombre'),
      users ? roles.find(role => role.id === item.idRol)?.name ?? 'Sin rol informado' : String(item.estado ?? 'Sin estado')]);
    paint();
  } catch (error) {
    if (version === current) status.textContent = error instanceof Error ? error.message : 'No se pudieron cargar los datos.';
  } finally {
    if (version === current) filter.disabled = false;
  }
}

function sync() {
  const session = getSession();
  const nextToken = session?.roles.includes('ADMINISTRADOR') ? session.accessToken : undefined;
  if (nextToken === token) return;
  token = nextToken;
  nav.hidden = !token;
  section.hidden = true;
  rows = [];
  body.replaceChildren();
  version++;
  if (token) void choose('refunds');
}

buttons.forEach(button => button.addEventListener('click', () => void choose(button.dataset.adminView as Directory)));
filter.addEventListener('input', () => { page = 0; paint(); });
previous.addEventListener('click', () => { page--; paint(); });
next.addEventListener('click', () => { page++; paint(); });
window.addEventListener('ticketya:profile', sync);
window.addEventListener('pageshow', sync);
sync();
