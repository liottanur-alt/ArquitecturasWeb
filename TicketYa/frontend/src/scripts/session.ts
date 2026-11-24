const key = 'ticketya-auth-session';

export type AuthSession = { accessToken: string; userId: number; expiresAt: number; roles: string[] };

// Estos claims se usan para la interfaz. El Gateway valida el JWT y autoriza las operaciones.
function readToken(accessToken: string): AuthSession {
  try {
    const parts = accessToken.split('.');
    if (parts.length !== 3 || parts.some(part => !/^[\w-]+$/.test(part))) throw new Error();
    const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
    const userId = Number(payload.sub);
    const expiresAt = Number(payload.exp) * 1000;
    if (typeof payload.sub !== 'string' || !/^\d+$/.test(payload.sub) || !Number.isSafeInteger(userId) || userId <= 0
      || !Number.isSafeInteger(expiresAt) || expiresAt <= Date.now()) throw new Error();
    const roles = Array.isArray(payload.roles) ? payload.roles.filter((role: unknown): role is string => typeof role === 'string') : [];
    return { accessToken, userId, expiresAt, roles };
  } catch {
    throw new Error('El servidor devolvió una sesión inválida o vencida.');
  }
}

export function getSession(): AuthSession | null {
  try {
    const accessToken = sessionStorage.getItem(key);
    return accessToken === null ? null : readToken(accessToken);
  } catch {
    try { sessionStorage.removeItem(key); } catch { /* Almacenamiento no disponible. */ }
    return null;
  }
}

export function startSession(response: unknown): AuthSession {
  if (!response || typeof response !== 'object' || !('accessToken' in response)
    || typeof response.accessToken !== 'string') throw new Error('El servidor no devolvió un token de acceso.');
  const session = readToken(response.accessToken);
  sessionStorage.setItem(key, session.accessToken);
  window.dispatchEvent(new Event('ticketya:profile'));
  return session;
}

export function getAuthenticatedUserId() {
  return getSession()?.userId ?? null;
}

export function clearSession() {
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
