import gsap from 'gsap';
import { api, ApiError } from './api';
import { safeReturn, startSession } from './session';

const form = document.querySelector<HTMLFormElement>('#login-form')!;
const email = document.querySelector<HTMLInputElement>('#login-email')!;
const password = document.querySelector<HTMLInputElement>('#login-password')!;
const submit = document.querySelector<HTMLButtonElement>('#login-submit')!;
const submitLabel = document.querySelector<HTMLElement>('#login-submit-label')!;
const alert = document.querySelector<HTMLElement>('#login-error')!;
const status = document.querySelector<HTMLElement>('#login-status')!;
const returnTo = safeReturn(new URLSearchParams(location.search).get('volver') || '/perfil/');
document.querySelector<HTMLAnchorElement>('#register-link')!.href = '/registro/?volver=' + encodeURIComponent(returnTo);

form.addEventListener('submit', async event => {
  event.preventDefault();
  if (submit.disabled || !form.reportValidity()) return;
  submit.disabled = true;
  submit.classList.add('loading');
  submitLabel.textContent = 'Iniciando sesión…';
  alert.hidden = true;
  form.setAttribute('aria-busy', 'true');
  status.textContent = 'Iniciando sesión…';
  try {
    const { data } = await api('/auth/login', {
      method: 'POST', body: { email: email.value.trim(), password: password.value }
    });
    startSession(data);
    password.value = '';
    location.assign(returnTo);
  } catch (cause) {
    document.querySelector('#login-error-text')!.textContent = cause instanceof ApiError && cause.status === 401
      ? 'El email o la contraseña son incorrectos.'
      : cause instanceof Error ? cause.message : 'No pudimos iniciar sesión.';
    status.textContent = '';
    alert.hidden = false;
    alert.focus();
    submitLabel.textContent = 'Iniciar sesión';
    submit.classList.remove('loading');
    submit.disabled = false;
  } finally {
    form.removeAttribute('aria-busy');
  }
});

if (!matchMedia('(prefers-reduced-motion: reduce)').matches) {
  gsap.from('.story-content > *', { opacity: 0, y: 25, duration: .7, stagger: .1, ease: 'power3.out', clearProps: 'all' });
  gsap.from('.login-form-content > *', { opacity: 0, y: 20, duration: .6, stagger: .07, ease: 'power3.out', clearProps: 'all' });
}
