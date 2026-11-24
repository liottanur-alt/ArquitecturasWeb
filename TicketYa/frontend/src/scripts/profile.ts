import { initRecommendations } from './recommendations';
import { initPreferences } from './preferences';
import { profileBody, profileLimits, validateProfile, validProfile, type Profile, type ProfileValues, type ProfileField } from './profile-data';
import { api, ApiError } from './api';
import gsap from 'gsap';
import { initTickets } from './tickets';
import { initHistory } from './history';

import { getAuthenticatedUserId, getSession, clearSession } from './session';
import { initCart } from './cart-view';
const owner = getAuthenticatedUserId();
const isCurrentOwner = () => owner !== null && getAuthenticatedUserId() === owner;
const main = document.querySelector<HTMLElement>('#profile-main')!;
const fields = document.querySelector<HTMLFieldSetElement>('#profile-fields')!;
const input = (id: string) => document.getElementById(id) as HTMLInputElement;
const notice = document.querySelector<HTMLElement>('#profile-status')!;
const alert = document.querySelector<HTMLElement>('#profile-error')!;
let profile: Profile | null = null;
let busy = false;
const form = document.querySelector<HTMLFormElement>('#profile-form')!;
const retry = document.querySelector<HTMLButtonElement>('#retry-profile')!;
const saveLabel = document.querySelector<HTMLElement>('#save-profile-label')!;
const names = Object.keys(profileLimits) as ProfileField[];
const values = () => Object.fromEntries(names.map(name => [name, input(name).value])) as ProfileValues;
function validate() {
  const errors = validateProfile(values());
  for (const name of names) {
    input(name).setCustomValidity(errors[name] || '');
    input(name).setAttribute('aria-invalid', String(!!errors[name]));
    document.getElementById(name + '-error')!.textContent = errors[name] || '';
  }
  return Object.keys(errors).length === 0;
}
form.addEventListener('input', () => { validate(); alert.hidden = true; });
function goToLogin() {
  main.hidden = true;
  location.replace('/login/?volver=' + encodeURIComponent('/perfil/' + location.search + location.hash));
}
function showProfile(data: Profile) { profile = data; for (const name of ['nombre', 'email', 'dni', 'ciudad'] as const) input(name).value = data[name]; document.querySelectorAll('[data-full-name]').forEach(e => e.textContent = data.nombre); document.querySelectorAll('[data-profile-email]').forEach(e => e.textContent = data.email); document.querySelector('[data-first-name]')!.textContent = data.nombre.trim().split(/\s+/)[0]; document.querySelector('[data-initials]')!.textContent = data.nombre.split(/\s+/).slice(0, 2).map(s => s[0]).join('').toUpperCase(); fields.disabled = false; }
async function load() {
    if (busy || !isCurrentOwner()) return;
    busy = true; profile = null; alert.hidden = true; fields.disabled = true; retry.disabled = true; form.setAttribute('aria-busy', 'true'); notice.textContent = 'Cargando tus datos…';
    try {
        const { data } = await api(`/usuarios/${owner}`);
        if (!isCurrentOwner()) return;
        if (!validProfile(data) || data.id !== owner) throw new Error('El servidor devolvió un perfil inválido.');
        showProfile(data); notice.textContent = '';
    } catch (cause) {
        if (!isCurrentOwner()) return;
        if (cause instanceof ApiError && cause.status === 404) { clearSession(); goToLogin(); return; }
        notice.textContent = ''; document.querySelector('#profile-error-text')!.textContent = cause instanceof Error ? cause.message : 'No pudimos cargar tus datos.'; alert.hidden = false;
    }
    finally { busy = false; retry.disabled = false; form.removeAttribute('aria-busy'); }
}
function tab(name: string) {
    const active = ['datos', 'compras', 'entradas', 'carrito', 'recomendaciones', 'notificaciones', 'preferencias'].includes(name) ? name : 'entradas';
    document.querySelectorAll<HTMLButtonElement>('[data-tab]').forEach(button => {
        const selected = button.dataset.tab === active;
        button.setAttribute('aria-selected', String(selected));
        button.tabIndex = selected ? 0 : -1;
        if (selected && matchMedia('(max-width: 760px)').matches) {
            const rail = button.parentElement;
            if (rail) rail.scrollLeft = button.offsetLeft - 4;
        }
    });
    document.querySelectorAll<HTMLElement>('[data-panel]').forEach(panel => panel.hidden = panel.dataset.panel !== active);
    history.replaceState(null, '', '#' + active);
}
if (owner === null) { goToLogin(); } else {
    main.hidden = false;
    const navigation = document.querySelector('.profile-tabs')!;
    const mobile = matchMedia('(max-width: 760px)');
    const syncOrientation = () => navigation.setAttribute('aria-orientation', mobile.matches ? 'horizontal' : 'vertical');
    mobile.addEventListener?.('change', syncOrientation);
    syncOrientation();
    const identity = document.querySelector<HTMLElement>('#identity-label')!;
    const roles: Record<string, string> = { CLIENTE: 'Cliente', ORGANIZADOR: 'Organización', ADMINISTRADOR: 'Administración', PERSONAL_ACCESO: 'Control de acceso' };
    identity.textContent = (getSession()?.roles ?? []).map(role => roles[role]).filter(Boolean).join(' · ');
    identity.hidden = !identity.textContent;
    load(); initRecommendations(owner); initPreferences(owner); initHistory(owner); initCart(); initTickets(); tab(location.hash.slice(1));
    document.querySelectorAll<HTMLButtonElement>('[data-tab]').forEach(b => b.addEventListener('click', () => tab(b.dataset.tab!)));
    document.querySelector('#personalize-recommendations')?.addEventListener('click', event => {
        const click = event as MouseEvent;
        if (click.metaKey || click.ctrlKey || click.shiftKey || click.altKey) return;
        event.preventDefault();
        tab('preferencias');
        document.querySelector<HTMLElement>('#preferences-heading')?.focus();
    });
    document.querySelector('.profile-tabs')!.addEventListener('keydown', e => { const event = e as KeyboardEvent; const buttons = [...document.querySelectorAll<HTMLButtonElement>('[data-tab]')]; const index = buttons.indexOf(document.activeElement as HTMLButtonElement); if (index < 0) return; let next = index; if (event.key === 'ArrowDown' || event.key === 'ArrowRight') next = (index + 1) % buttons.length; else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') next = (index + buttons.length - 1) % buttons.length; else if (event.key === 'Home') next = 0; else if (event.key === 'End') next = buttons.length - 1; else return; event.preventDefault(); buttons[next].focus(); tab(buttons[next].dataset.tab!); });
    window.addEventListener('hashchange', () => tab(location.hash.slice(1)));
    document.querySelector('#retry-profile')!.addEventListener('click', load);
    document.querySelector('#profile-form')!.addEventListener('submit', async e => {
        e.preventDefault(); if (!isCurrentOwner() || !profile || fields.disabled || busy) return;
        if (!validate()) { form.reportValidity(); return; }
        const body = profileBody(values(), profile.idRol);
        busy = true; fields.disabled = true; retry.disabled = true; alert.hidden = true;
        form.setAttribute('aria-busy', 'true'); saveLabel.textContent = 'Guardando…'; notice.textContent = 'Guardando cambios…';
        try {
            const { data } = await api(`/usuarios/${owner}`, { method: 'PUT', body });
            if (!isCurrentOwner()) return;
            if (!validProfile(data) || data.id !== owner || data.idRol !== body.idRol) throw new Error('El servidor devolvió un perfil inválido.');
            showProfile(data); notice.textContent = 'Tus datos se actualizaron.';
        } catch (cause) {
            if (!isCurrentOwner()) return;
            document.querySelector('#profile-error-text')!.textContent = cause instanceof ApiError && cause.status === 409 ? 'No se pudo guardar: ese email o DNI puede estar registrado, o hubo un conflicto concurrente. Revisá tus datos.' : cause instanceof Error ? cause.message : 'No pudimos confirmar los cambios.'; alert.hidden = false; notice.textContent = '';
        }
        finally { busy = false; retry.disabled = false; form.removeAttribute('aria-busy'); saveLabel.textContent = 'Guardar cambios'; if (profile && isCurrentOwner()) fields.disabled = false; }
    });
    const dialog = document.querySelector<HTMLDialogElement>('#purchase-dialog')!; document.querySelectorAll('[data-close-purchase]').forEach(b => b.addEventListener('click', () => dialog.close()));
    function hideInactiveProfile() {
        if (isCurrentOwner()) return;
        main.hidden = true;
        document.querySelectorAll<HTMLDialogElement>('dialog[open]').forEach(openDialog => openDialog.close());
    }
    function checkSession() {
        if (isCurrentOwner()) return;
        hideInactiveProfile();
        if (getAuthenticatedUserId() === null) goToLogin();
        else location.reload();
    }
    document.addEventListener('visibilitychange', () => { if (!document.hidden) checkSession(); });
    window.addEventListener('pageshow', checkSession);
    window.addEventListener('ticketya:profile', hideInactiveProfile);
    if (!matchMedia('(prefers-reduced-motion: reduce)').matches) gsap.from('.profile-welcome,.profile-sidebar,.profile-panels', { opacity: 0, y: 20, duration: .6, stagger: .08, clearProps: 'all' });
}
