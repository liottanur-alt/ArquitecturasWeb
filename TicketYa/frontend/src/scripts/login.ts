import gsap from 'gsap';
import { getSession, saveSession, safeReturn } from './session';

const form = document.querySelector<HTMLFormElement>('#login-form')!;
const email = document.querySelector<HTMLInputElement>('#email')!, password = document.querySelector<HTMLInputElement>('#password')!;
const toggle = document.querySelector<HTMLButtonElement>('#toggle-password')!;
const submit = document.querySelector<HTMLButtonElement>('#login-submit')!;
const error = document.querySelector<HTMLElement>('#login-error')!;
const status = document.querySelector<HTMLElement>('#login-status')!;
const returnTo = safeReturn(new URLSearchParams(location.search).get('volver'));
if (getSession()) location.replace(returnTo);
document.querySelector<HTMLAnchorElement>('#register-link')!.href = '/registro/?volver=' + encodeURIComponent(returnTo);
document.querySelector<HTMLElement>('#registration-success')!.hidden = new URLSearchParams(location.search).get('registro') !== 'ok';
toggle.addEventListener('click', () => {
  const show = password.type === 'password'; password.type = show ? 'text' : 'password';
  toggle.setAttribute('aria-pressed', String(show)); toggle.setAttribute('aria-label', show ? 'Ocultar contraseña' : 'Mostrar contraseña');
});
const apiBase = (import.meta.env.PUBLIC_API_BASE_URL || '/api').replace(/\/$/, '');
let submitting = false;
form.addEventListener('submit', async e => {
  e.preventDefault(); if (submitting || !form.reportValidity()) return;
  submitting = true; error.hidden = true; submit.disabled = true; submit.classList.add('loading');
  form.setAttribute('aria-busy', 'true'); status.textContent = 'Verificando tus datos…';
  document.querySelector('#submit-label')!.textContent = 'Ingresando…';
  const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 12000);
  const submittedEmail = email.value.trim();
  try {
    const response = await fetch(`${apiBase}/auth/login`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: submittedEmail, password: password.value }), signal: controller.signal, credentials: 'omit'
    });
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) throw new Error('credentials');
      if (response.status === 429) throw new Error('rate-limit');
      if (response.status === 400) throw new Error('bad-request');
      throw new Error('unavailable');
    }
    let data: unknown;
    try { data = await response.json(); } catch { throw new Error('invalid-response'); }
    saveSession(data, submittedEmail); password.value = ''; status.textContent = 'Sesión iniciada. Volviendo a tu plan.';
    location.assign(returnTo);
  } catch (cause) {
    const code = cause instanceof Error ? cause.message : '';
    const messages: Record<string, string> = {
      credentials: 'El email o la contraseña no son correctos. Revisalos e intentá de nuevo.',
      'rate-limit': 'Hubo varios intentos. Esperá un momento antes de volver a ingresar.',
      'bad-request': 'Revisá el email y la contraseña e intentá nuevamente.',
      'invalid-response': 'No pudimos completar el ingreso. Intentá nuevamente en unos momentos.'
    };
    document.querySelector('#login-error-text')!.textContent = messages[code] || 'No pudimos conectar con el servicio. Intentá nuevamente en unos momentos.';
    error.hidden = false; error.focus(); status.textContent = 'No se pudo iniciar sesión.';
  } finally {
    clearTimeout(timeout); submitting = false; submit.disabled = false; submit.classList.remove('loading'); form.removeAttribute('aria-busy');
    document.querySelector('#submit-label')!.textContent = 'Ingresar a mi cuenta';
  }
});
if (!matchMedia('(prefers-reduced-motion: reduce)').matches) {
  gsap.from('.story-content > *', { opacity: 0, y: 25, duration: .7, stagger: .1, ease: 'power3.out', clearProps: 'all' });
  gsap.from('.login-form-content > *', { opacity: 0, y: 20, duration: .6, stagger: .07, ease: 'power3.out', clearProps: 'all' });
}
