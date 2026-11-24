import { api } from './api';
import { clearSession, getSession, safeReturn } from './session';

const header = document.querySelector<HTMLElement>('[data-shell-header]')!;
const menu = header.querySelector<HTMLDialogElement>('.shell-mobile')!;
const menuTrigger = header.querySelector<HTMLButtonElement>('[data-shell-menu-trigger]')!;
const accountMenu = header.querySelector<HTMLDetailsElement>('[data-shell-account]')!;
let profileToken: string | undefined;
let displayName = '';

function paintIdentity() {
  const initials = displayName.split(/\s+/).filter(Boolean).slice(0, 2).map(word => [...word][0]).join('').toLocaleUpperCase('es-AR');
  header.querySelector('[data-shell-name]')!.textContent = displayName || 'Mi cuenta';
  header.querySelector('[data-shell-initials]')!.textContent = initials || 'TY';
  accountMenu.querySelector('summary')!.setAttribute('aria-label', displayName ? 'Menú de ' + displayName : 'Menú de cuenta');
}

function paintActive() {
  const path = location.pathname.replace(/\/+$/, '');
  const key = !path || path === '/eventos' ? 'explorar' : path === '/perfil'
    ? location.hash === '#entradas' ? 'entradas' : location.hash === '#compras' ? 'compras' : 'perfil'
    : path.slice(1);
  header.querySelectorAll<HTMLElement>('[data-shell-route]').forEach(link => {
    if (link.dataset.shellRoute === key) link.setAttribute('aria-current', 'page');
    else link.removeAttribute('aria-current');
  });
}

async function loadIdentity(token: string, id: number) {
  try {
    const { data } = await api<{ id: number; nombre: string }>('/usuarios/' + id, { cache: 'no-store' });
    if (getSession()?.accessToken !== token || data?.id !== id || typeof data.nombre !== 'string') return;
    displayName = data.nombre.trim();
    paintIdentity();
  } catch { /* El menú conserva un nombre genérico si no se puede consultar el perfil. */ }
}

function sync(forceIdentity = false) {
  const session = getSession();
  document.querySelectorAll<HTMLElement>('[data-shell-guest]').forEach(group => group.hidden = !!session);
  document.querySelectorAll<HTMLElement>('[data-shell-member]').forEach(group => group.hidden = !session);
  document.querySelectorAll<HTMLElement>('[data-shell-roles]').forEach(link => {
    link.hidden = !session?.roles.some(role => link.dataset.shellRoles?.split(' ').includes(role));
  });
  accountMenu.hidden = !session;
  if (!session) accountMenu.open = false;
  const onAuth = /^\/(login|registro)\/?$/.test(location.pathname);
  const next = onAuth ? new URLSearchParams(location.search).get('volver') : location.pathname + location.search + location.hash;
  const returnTo = encodeURIComponent(safeReturn(next));
  header.querySelectorAll<HTMLAnchorElement>('[data-shell-login], [data-shell-register]').forEach(link => {
    link.href = (link.hasAttribute('data-shell-login') ? '/login/' : '/registro/') + '?volver=' + returnTo;
  });
  paintActive();
  const token = session?.accessToken;
  if (token !== profileToken) {
    profileToken = token;
    displayName = '';
    paintIdentity();
    if (menu.open) menu.close();
    accountMenu.open = false;
    if (session) void loadIdentity(session.accessToken, session.userId);
  } else if (session && forceIdentity) void loadIdentity(session.accessToken, session.userId);
}

function compact() { header.toggleAttribute('data-scrolled', window.scrollY > 24); }
window.addEventListener('scroll', compact, { passive: true });
window.addEventListener('hashchange', () => sync());
window.addEventListener('pageshow', () => { sync(); compact(); });
window.addEventListener('ticketya:profile', () => sync(true));
document.addEventListener('visibilitychange', () => { if (!document.hidden) sync(); });
menuTrigger.addEventListener('click', () => { accountMenu.open = false; menu.showModal(); });
header.querySelector('[data-shell-menu-close]')!.addEventListener('click', () => menu.close());
menu.addEventListener('click', event => {
  if ((event.target as Element).closest('a[href]')) menu.close();
  else if (event.target === menu) {
    const bounds = menu.getBoundingClientRect();
    if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) menu.close();
  }
});
const desktop = window.matchMedia('(min-width: 961px)');
desktop.addEventListener('change', () => { if (desktop.matches && menu.open) menu.close(); });
header.querySelectorAll('[data-shell-logout]').forEach(button => button.addEventListener('click', () => {
  try { clearSession(); location.assign('/login/'); }
  catch { window.alert('No pudimos cerrar sesión. Revisá el almacenamiento de esta pestaña.'); }
}));
document.addEventListener('click', event => {
  if (accountMenu.open && (!accountMenu.contains(event.target as Node) || (event.target as Element).closest('a[href]'))) accountMenu.open = false;
});
document.addEventListener('keydown', event => {
  if (event.key === 'Escape' && accountMenu.open) {
    accountMenu.open = false;
    accountMenu.querySelector('summary')?.focus();
  }
});
sync();
compact();
