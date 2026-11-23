import gsap from 'gsap';
import { api, ApiError, ApiConnectionError } from './api';
import { clientRoleId, profileBody, profileLimits, validateProfile, validProfile, type ProfileField, type ProfileValues } from './profile-data';
import { safeReturn } from './session';

const form = document.querySelector<HTMLFormElement>('#register-form')!;
const fields = document.querySelector<HTMLFieldSetElement>('#register-fields')!;
const status = document.querySelector<HTMLElement>('#register-status')!;
const alert = document.querySelector<HTMLElement>('#register-error')!;
const retry = document.querySelector<HTMLButtonElement>('#retry-roles')!;
const label = document.querySelector<HTMLElement>('#register-submit-label')!;
const button = document.querySelector<HTMLButtonElement>('#register-submit')!;
const names = Object.keys(profileLimits) as ProfileField[];
const input = (name: ProfileField) => document.getElementById(name) as HTMLInputElement;
const values = () => Object.fromEntries(names.map(name => [name, input(name).value])) as ProfileValues;
const returnTo = safeReturn(new URLSearchParams(location.search).get('volver') || '/perfil/');
document.querySelector<HTMLAnchorElement>('#select-profile-link')!.href = '/login/?volver=' + encodeURIComponent(returnTo);
let roleId: number | null = null;
let busy = false, created = false;
function showError(message: string) {
  document.querySelector('#register-error-text')!.textContent = message;
  alert.hidden = false; alert.focus();
}
function validate() {
  const errors = validateProfile(values());
  for (const name of names) {
    input(name).setCustomValidity(errors[name] || '');
    input(name).setAttribute('aria-invalid', String(!!errors[name]));
    document.getElementById(name + '-error')!.textContent = errors[name] || '';
  }
  return Object.keys(errors).length === 0;
}
async function loadRole() {
  if (busy || created) return;
  busy = true; roleId = null; fields.disabled = true; retry.hidden = true; alert.hidden = true;
  form.setAttribute('aria-busy', 'true'); status.textContent = 'Cargando el rol CLIENTE…';
  try {
    roleId = clientRoleId((await api('/roles')).data);
    fields.disabled = false; status.textContent = 'Completá tus datos para crear el perfil.';
  } catch (cause) {
    showError(cause instanceof Error ? cause.message : 'No pudimos consultar los roles.');
    status.textContent = 'No se puede crear el perfil hasta obtener el rol CLIENTE.'; retry.hidden = false;
  } finally { busy = false; form.removeAttribute('aria-busy'); }
}
retry.addEventListener('click', loadRole);
form.addEventListener('input', () => { validate(); alert.hidden = true; });
form.addEventListener('submit', async event => {
  event.preventDefault();
  if (busy || created || roleId === null) return;
  if (!validate()) { form.reportValidity(); return; }
  busy = true; fields.disabled = true; alert.hidden = true; button.classList.add('loading');
  form.setAttribute('aria-busy', 'true'); label.textContent = 'Creando perfil…'; status.textContent = 'Guardando el perfil en el servidor…';
  try {
    const { data } = await api('/usuarios', { method: 'POST', body: profileBody(values(), roleId) });
    if (!validProfile(data) || data.idRol !== roleId) throw new Error('No pudimos confirmar los datos del perfil creado. Consultá los perfiles existentes antes de volver a enviar.');
    created = true;
    document.querySelector<HTMLElement>('#register-success')!.hidden = false;
    status.textContent = 'Perfil #' + data.id + ' creado. Elegilo explícitamente desde el selector de perfiles.';
    label.textContent = 'Perfil creado';
  } catch (cause) {
    showError(cause instanceof ApiError && cause.status === 409
      ? 'No se pudo guardar: ese email o DNI puede estar registrado, o hubo un conflicto concurrente. Revisá los perfiles existentes y tus datos.'
      : cause instanceof ApiConnectionError
        ? cause.message + ' Consultá los perfiles existentes antes de volver a enviar.'
        : cause instanceof Error ? cause.message : 'No pudimos confirmar el alta.');
    status.textContent = 'El alta no pudo confirmarse.'; label.textContent = 'Crear perfil';
  } finally {
    busy = false; fields.disabled = created; button.classList.remove('loading'); form.removeAttribute('aria-busy');
  }
});
loadRole();
if (!matchMedia('(prefers-reduced-motion: reduce)').matches) {
  gsap.from('.story-content > *', { opacity: 0, y: 25, duration: .7, stagger: .1, clearProps: 'all' });
  gsap.from('.login-form-content > *', { opacity: 0, y: 20, duration: .6, stagger: .07, clearProps: 'all' });
}
