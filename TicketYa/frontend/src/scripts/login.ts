import gsap from 'gsap';
import { api } from './api';
import { getSelectedProfileId, selectProfile, safeReturn } from './session';

type Choice = { id: number; nombre: string; email: string };
const form = document.querySelector<HTMLFormElement>('#login-form')!;
const select = document.querySelector<HTMLSelectElement>('#demo-profile')!;
const submit = document.querySelector<HTMLButtonElement>('#login-submit')!;
const retry = document.querySelector<HTMLButtonElement>('#retry-profiles')!;
const error = document.querySelector<HTMLElement>('#login-error')!;
const status = document.querySelector<HTMLElement>('#login-status')!;
const returnTo = safeReturn(new URLSearchParams(location.search).get('volver') || '/perfil/');
document.querySelector<HTMLAnchorElement>('#register-link')!.href = '/registro/?volver=' + encodeURIComponent(returnTo);
let choices: Choice[] = [];
function showError(cause: unknown) {
  document.querySelector('#login-error-text')!.textContent = cause instanceof Error ? cause.message : 'No pudimos cargar los perfiles.';
  error.hidden = false;
}
async function loadProfiles() {
  select.disabled = true; submit.disabled = true; retry.hidden = true; error.hidden = true;
  form.setAttribute('aria-busy', 'true'); status.textContent = 'Cargando perfiles…';
  select.replaceChildren(new Option('Elegí un perfil', '')); choices = [];
  try {
    const { data } = await api('/usuarios');
    if (!Array.isArray(data) || !data.every(value => value && Number.isSafeInteger(value.id) && value.id > 0
      && typeof value.nombre === 'string' && typeof value.email === 'string')) {
      throw new Error('El servidor devolvió una lista de perfiles inválida.');
    }
    choices = data;
    for (const profile of choices) select.add(new Option(profile.nombre + ' · ' + profile.email + ' · #' + profile.id, String(profile.id)));
    const selectedId = getSelectedProfileId();
    if (choices.some(profile => profile.id === selectedId)) select.value = String(selectedId);
    select.disabled = choices.length === 0; submit.disabled = !select.value;
    status.textContent = choices.length ? 'Elegí un perfil y confirmá la selección.' : 'No hay perfiles disponibles. No se seleccionó ningún perfil.';
    retry.hidden = choices.length > 0;
  } catch (cause) {
    showError(cause); status.textContent = 'No se pudo cargar la lista. Tu selección anterior se conserva.'; retry.hidden = false;
  } finally { form.removeAttribute('aria-busy'); }
}
select.addEventListener('change', () => { submit.disabled = !select.value; error.hidden = true; });
retry.addEventListener('click', loadProfiles);
form.addEventListener('submit', event => {
  event.preventDefault();
  const profile = choices.find(profile => String(profile.id) === select.value);
  if (!profile || select.disabled || submit.disabled) return;
  try { selectProfile(profile.id); location.assign(returnTo); }
  catch { showError(new Error('No pudimos guardar la selección. Habilitá el almacenamiento de esta pestaña e intentá nuevamente.')); }
});
loadProfiles();
if (!matchMedia('(prefers-reduced-motion: reduce)').matches) {
  gsap.from('.story-content > *', { opacity: 0, y: 25, duration: .7, stagger: .1, ease: 'power3.out', clearProps: 'all' });
  gsap.from('.login-form-content > *', { opacity: 0, y: 20, duration: .6, stagger: .07, ease: 'power3.out', clearProps: 'all' });
}
