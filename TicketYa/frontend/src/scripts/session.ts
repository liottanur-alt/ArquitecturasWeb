// Esta selección es solo contexto de demostración, nunca una credencial.
const key = 'ticketya-demo-profile-id';
function validId(value: unknown): value is number { return Number.isSafeInteger(value) && Number(value) > 0; }
export function getSelectedProfileId(): number | null {
  try {
    sessionStorage.removeItem('ticketya-session'); // Descartar sesiones JWT heredadas.
    const raw = sessionStorage.getItem(key);
    const id: unknown = raw === null ? null : JSON.parse(raw);
    if (!validId(id)) { sessionStorage.removeItem(key); return null; }
    return id;
  } catch { return null; }
}
export function selectProfile(id: number) {
  if (!validId(id)) throw new Error('El perfil debe tener un ID válido.');
  sessionStorage.setItem(key, JSON.stringify(id));
  window.dispatchEvent(new Event('ticketya:profile'));
}
export function clearSelectedProfile() {
  sessionStorage.removeItem(key);
  window.dispatchEvent(new Event('ticketya:profile'));
}
export function safeReturn(value: string | null) {
  if (!value || !value.startsWith('/') || value.startsWith('//')) return '/';
  try {
    const url = new URL(value, window.location.origin);
    if (url.origin !== window.location.origin || /^\/(login|registro)\/?$/.test(url.pathname)) return '/';
    return url.pathname + url.search + url.hash;
  } catch { return '/'; }
}
