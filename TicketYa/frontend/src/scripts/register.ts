import gsap from 'gsap';
import { api, ApiError, ApiConnectionError } from './api';
import { profileLimits, validateProfile, validProfile, type ProfileField, type ProfileValues } from './profile-data';
import { safeReturn } from './session';

const form = document.querySelector<HTMLFormElement>('#register-form')!;
const fields = document.querySelector<HTMLFieldSetElement>('#register-fields')!;
const status = document.querySelector<HTMLElement>('#register-status')!;
const alert = document.querySelector<HTMLElement>('#register-error')!;
const label = document.querySelector<HTMLElement>('#register-submit-label')!;
const button = document.querySelector<HTMLButtonElement>('#register-submit')!;
const names = Object.keys(profileLimits) as ProfileField[];
const input = (name: ProfileField | 'password') => document.getElementById(name) as HTMLInputElement;
const values = () => Object.fromEntries(names.map(name => [name, input(name).value.trim()])) as ProfileValues;
const returnTo = safeReturn(new URLSearchParams(location.search).get('volver') || '/perfil/');
const loginPath = '/login/?volver=' + encodeURIComponent(returnTo);
document.querySelector<HTMLAnchorElement>('#select-profile-link')!.href = loginPath;
let created = false;

function showError(message: string) {
  document.querySelector('#register-error-text')!.textContent = message;
  alert.hidden = false;
  alert.focus();
}

function validate() {
  const password = input('password').value;
  const errors = {
    ...validateProfile(values()),
    password: !password.trim() || password.length < 4 || new TextEncoder().encode(password).length > 72
      ? 'Ingresá una contraseña de al menos 4 caracteres y hasta 72 bytes.' : ''
  };
  for (const name of [...names, 'password'] as const) {
    input(name).setCustomValidity(errors[name] || '');
    input(name).setAttribute('aria-invalid', String(!!errors[name]));
    document.getElementById(name + '-error')!.textContent = errors[name] || '';
  }
  return !Object.values(errors).some(Boolean);
}

form.addEventListener('input', () => { validate(); alert.hidden = true; });
form.addEventListener('submit', async event => {
  event.preventDefault();
  if (fields.disabled) return;
  if (!validate()) { form.reportValidity(); return; }
  fields.disabled = true;
  alert.hidden = true;
  button.classList.add('loading');
  form.setAttribute('aria-busy', 'true');
  label.textContent = 'Creando cuenta…';
  status.textContent = 'Guardando tus datos…';
  try {
    const { data } = await api('/auth/registro', {
      method: 'POST', body: { ...values(), password: input('password').value }
    });
    if (!validProfile(data)) throw new Error('No pudimos confirmar el alta. Probá iniciar sesión antes de volver a enviar el formulario.');
    created = true;
    input('password').value = '';
    location.assign(loginPath);
  } catch (cause) {
    showError(cause instanceof ApiError && cause.status === 409
      ? 'Ese email o DNI ya puede estar registrado. Revisá tus datos o probá iniciar sesión.'
      : cause instanceof ApiConnectionError
        ? cause.message + ' Probá iniciar sesión antes de volver a enviar el formulario.'
        : cause instanceof Error ? cause.message : 'No pudimos confirmar el alta.');
    status.textContent = '';
    label.textContent = 'Crear cuenta';
  } finally {
    fields.disabled = created;
    button.classList.remove('loading');
    form.removeAttribute('aria-busy');
  }
});

if (!matchMedia('(prefers-reduced-motion: reduce)').matches) {
  gsap.from('.story-content > *', { opacity: 0, y: 25, duration: .7, stagger: .1, clearProps: 'all' });
  gsap.from('.login-form-content > *', { opacity: 0, y: 20, duration: .6, stagger: .07, clearProps: 'all' });
}
