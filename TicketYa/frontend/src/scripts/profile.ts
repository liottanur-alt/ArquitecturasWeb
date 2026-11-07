import gsap from 'gsap';
import { initTickets } from './tickets';
import { catalog } from '../data/events';
import { demoPurchases } from '../data/purchases';
import { getSession, clearSession } from './session';
import { getCart, removeDraft, seedDemoCart } from './cart';
type Profile = { id: number; nombre: string; email: string; dni: string; ciudad: string; idRol: number };
const session = getSession(); const demo = !session && new URLSearchParams(location.search).get('demo') === '1';
const main = document.querySelector<HTMLElement>('#profile-main')!;
const fields = document.querySelector<HTMLFieldSetElement>('#profile-fields')!;
const input = (id: string) => document.getElementById(id) as HTMLInputElement;
const notice = document.querySelector<HTMLElement>('#profile-status')!;
const alert = document.querySelector<HTMLElement>('#profile-error')!;
const api = (import.meta.env.PUBLIC_API_BASE_URL || '/api').replace(/\/$/, '');
const money = (n: number) => '$ ' + n.toLocaleString('es-AR');
const esc = (s: unknown) => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]!));
let profile: Profile | null = null;
function login() { clearSession(); location.replace('/login/?volver=' + encodeURIComponent('/perfil/' + location.hash)); }
function showProfile(data: Profile) { profile = data; for (const name of ['nombre', 'email', 'dni', 'ciudad'] as const) input(name).value = data[name]; document.querySelectorAll('[data-full-name]').forEach(e => e.textContent = data.nombre); document.querySelectorAll('[data-profile-email]').forEach(e => e.textContent = data.email); document.querySelector('[data-first-name]')!.textContent = data.nombre.trim().split(/\s+/)[0]; document.querySelector('[data-initials]')!.textContent = data.nombre.split(/\s+/).slice(0, 2).map(s => s[0]).join('').toUpperCase(); fields.disabled = false; }
function validProfile(data: unknown): data is Profile { if (!data || typeof data !== 'object') return false; const d = data as Profile; return Number.isSafeInteger(d.id) && d.id > 0 && Number.isSafeInteger(d.idRol) && d.idRol > 0 && ['nombre', 'email', 'dni', 'ciudad'].every(k => typeof d[k as keyof Profile] === 'string'); }
async function load() {
    alert.hidden = true; fields.disabled = true; notice.textContent = 'Cargando tus datos…';
    if (demo) { let saved: unknown = null; try { saved = JSON.parse(sessionStorage.getItem('ticketya-perfil-demo') || 'null'); } catch { } showProfile(validProfile(saved) ? saved : { id: 0, nombre: 'Ana Pérez', email: 'ana@ejemplo.com', dni: '40123456', ciudad: 'Tandil', idRol: 1 }); notice.textContent = 'Los cambios de este perfil de ejemplo se guardan solo en esta pestaña.'; return; }
    try { const token = getSession(); if (!token) { login(); return; } const claims = JSON.parse(atob(token.accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))); if (!/^\d+$/.test(claims.sub)) throw new Error('invalid'); const response = await fetch(`${api}/usuarios/${claims.sub}`, { headers: { Authorization: `Bearer ${token.accessToken}` }, signal: AbortSignal.timeout(12000) }); if (response.status === 401) { login(); return; } if (!response.ok) throw new Error(); const data: unknown = await response.json(); if (!validProfile(data) || String(data.id) !== String(claims.sub)) throw new Error(); showProfile(data); notice.textContent = ''; }
    catch { notice.textContent = ''; document.querySelector('#profile-error-text')!.textContent = 'No pudimos cargar tus datos. Reintentá en unos momentos.'; alert.hidden = false; }
}
function tab(name: string) { const active = ['datos', 'compras', 'entradas', 'carrito'].includes(name) ? name : 'datos'; document.querySelectorAll<HTMLButtonElement>('[data-tab]').forEach(b => { const selected = b.dataset.tab === active; b.setAttribute('aria-selected', String(selected)); b.tabIndex = selected ? 0 : -1; }); document.querySelectorAll<HTMLElement>('[data-panel]').forEach(p => p.hidden = p.dataset.panel !== active); history.replaceState(null, '', '#' + active); }
function purchases(filter = 'Todas') {
    const list = document.querySelector('#purchase-list')!;
    list.innerHTML = demoPurchases.filter(p => filter === 'Todas' || p.status === filter).map(p => { const e = catalog.find(e => e.id === p.eventId)!; return `<article class="purchase-card"><img src="${esc(e.image)}" alt="" loading="lazy"/><div class="purchase-info"><div class="purchase-meta"><span>${esc(p.id)}</span><span class="purchase-badge ${p.status === 'Devuelta' ? 'returned' : ''}">${esc(p.status)}</span></div><h3>${esc(e.title)}</h3><p>${esc(p.detail)}</p><small>Comprada el ${esc(p.date)}</small><div class="purchase-bottom"><strong>${money(p.total)}</strong><button data-purchase="${p.id}">Ver detalle ↗</button><button data-purchase-tickets="${p.id}">Ver entradas ↗</button></div></div></article>`; }).join('');
    list.querySelectorAll<HTMLButtonElement>('[data-purchase]').forEach(b => b.addEventListener('click', () => { const p = demoPurchases.find(p => p.id === b.dataset.purchase)!; const e = catalog.find(e => e.id === p.eventId)!; document.querySelector('#purchase-title')!.textContent = e.title; document.querySelector('#purchase-detail')!.textContent = p.detail; document.querySelector('#purchase-payment')!.textContent = p.payment; document.querySelector('#purchase-total')!.textContent = money(p.total); document.querySelector<HTMLDialogElement>('#purchase-dialog')!.showModal(); }));
}
function cart() {
    const items = getCart(); let total = 0; document.querySelector('#cart-count')!.textContent = String(items.length);
    document.querySelector('#cart-list')!.innerHTML = items.map(d => { const e = catalog.find(e => e.id === d.eventId)!; let subtotal = 0; const detail = d.selection.map(item => { const sector = e.sectors.find(s => s.id === item.sectorId)!; subtotal += sector.price * item.quantity; return `${item.quantity} × ${sector.name}${item.seats.length ? ' · ' + item.seats.join(', ') : ' · Sin numerar'}`; }); total += subtotal; return `<article class="purchase-card cart-card"><img src="${esc(e.image)}" alt="" loading="lazy"/><div class="purchase-info"><div class="purchase-meta"><span>SELECCIÓN PENDIENTE</span><span class="cart-badge">Sin comprar</span></div><h3>${esc(e.title)}</h3><p>${esc(e.fullDate)}</p><p>${detail.map(esc).join('<br/>')}</p><div class="purchase-bottom"><strong>${money(subtotal)}</strong><a href="/eventos/${e.id}/?carrito=1">Continuar selección ↗</a></div><button class="remove-draft" data-remove-draft="${e.id}">Quitar del carrito</button></div></article>`; }).join('');
    document.querySelector<HTMLElement>('#cart-empty')!.hidden = items.length > 0; document.querySelector<HTMLElement>('#cart-total-box')!.hidden = items.length === 0; document.querySelector('#cart-total')!.textContent = money(total);
    document.querySelectorAll<HTMLButtonElement>('[data-remove-draft]').forEach(b => b.addEventListener('click', () => { try { removeDraft(Number(b.dataset.removeDraft)); cart(); document.querySelector('#cart-status')!.textContent = 'Selección quitada del carrito.'; } catch { document.querySelector('#cart-status')!.textContent = 'No pudimos actualizar el carrito en este navegador.'; } }));
}
if (!session && !demo) { login(); } else {
    main.hidden = false; document.querySelector<HTMLElement>('#profile-demo')!.hidden = !demo; if (demo) { seedDemoCart(); document.querySelector('#identity-label')!.textContent = 'Perfil de ejemplo'; } else document.querySelector('[data-profile-email]')!.textContent = session!.email;
    if (session) setTimeout(() => { main.hidden = true; login(); }, Math.min(Math.max(0, session.expiresAt - Date.now()), 2147483647));
    load(); purchases(); cart(); initTickets(); tab(location.hash.slice(1));
    document.querySelectorAll<HTMLButtonElement>('[data-tab]').forEach(b => b.addEventListener('click', () => tab(b.dataset.tab!)));
    document.querySelector('.profile-tabs')!.addEventListener('keydown', e => { const event = e as KeyboardEvent; const buttons = [...document.querySelectorAll<HTMLButtonElement>('[data-tab]')]; const index = buttons.indexOf(document.activeElement as HTMLButtonElement); if (index < 0) return; let next = index; if (event.key === 'ArrowDown' || event.key === 'ArrowRight') next = (index + 1) % buttons.length; else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') next = (index + buttons.length - 1) % buttons.length; else if (event.key === 'Home') next = 0; else if (event.key === 'End') next = buttons.length - 1; else return; event.preventDefault(); buttons[next].focus(); tab(buttons[next].dataset.tab!); });
    window.addEventListener('hashchange', () => tab(location.hash.slice(1)));
    document.querySelectorAll<HTMLButtonElement>('[data-history-filter]').forEach(b => b.addEventListener('click', () => { document.querySelectorAll('[data-history-filter]').forEach(el => el.setAttribute('aria-pressed', String(el === b))); purchases(b.dataset.historyFilter!); }));
    document.querySelector('#retry-profile')!.addEventListener('click', load);
    document.querySelector('#profile-form')!.addEventListener('submit', async e => {
        e.preventDefault(); if (!profile || fields.disabled) return; const body = { nombre: input('nombre').value.trim(), email: input('email').value.trim(), dni: input('dni').value.trim(), ciudad: input('ciudad').value.trim(), idRol: profile.idRol };
        if (!body.nombre || !body.dni || !body.ciudad || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email)) { notice.textContent = 'Completá todos los campos y revisá tu email.'; return; }
        fields.disabled = true; alert.hidden = true; notice.textContent = 'Guardando cambios…';
        try {
            if (demo) { const saved = { id: 1, ...body }; sessionStorage.setItem('ticketya-perfil-demo', JSON.stringify(saved)); showProfile(saved); notice.textContent = 'Cambios del perfil de ejemplo guardados en esta pestaña.'; return; }
            const token = getSession(); if (!token) { login(); return; } const response = await fetch(`${api}/usuarios/${profile.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token.accessToken}` }, body: JSON.stringify(body), signal: AbortSignal.timeout(12000) }); if (response.status === 401) { login(); return; } if (response.status === 409) throw new Error('duplicate'); if (!response.ok) throw new Error(); const data: unknown = await response.json(); if (!validProfile(data) || data.id !== profile.id) throw new Error(); showProfile(data); sessionStorage.setItem('ticketya-session', JSON.stringify({ ...token, email: data.email })); window.dispatchEvent(new Event('ticketya:session')); notice.textContent = 'Tus datos se actualizaron correctamente.';
        } catch (cause) { document.querySelector('#profile-error-text')!.textContent = cause instanceof Error && cause.message === 'duplicate' ? 'Ese email o DNI ya está registrado. Revisá tus datos.' : 'No pudimos confirmar los cambios. Intentá nuevamente.'; alert.hidden = false; notice.textContent = ''; }
        finally { if (profile) fields.disabled = false; }
    });
    const dialog = document.querySelector<HTMLDialogElement>('#purchase-dialog')!; document.querySelectorAll('[data-close-purchase]').forEach(b => b.addEventListener('click', () => dialog.close()));
    document.addEventListener('visibilitychange', () => { if (!demo && !getSession()) login(); }); window.addEventListener('ticketya:session', () => { if (!demo && !getSession()) login(); });
    if (!matchMedia('(prefers-reduced-motion: reduce)').matches) gsap.from('.profile-welcome,.profile-sidebar,.profile-panels', { opacity: 0, y: 20, duration: .6, stagger: .08, clearProps: 'all' });
}
