export type Session = { accessToken: string; expiresAt: number; email: string };
const key = 'ticketya-session';
export function clearSession() { try { sessionStorage.removeItem(key); } catch {} }
export function getSession(): Session | null {
  try {
    const data = JSON.parse(sessionStorage.getItem(key) || 'null');
    if (!data || typeof data.accessToken !== 'string' || !Number.isFinite(data.expiresAt)
      || data.expiresAt <= Date.now() || typeof data.email !== 'string') { clearSession(); return null; }
    return data;
  } catch { clearSession(); return null; }
}
export function saveSession(response: unknown, email: string) {
  const data = response as { accessToken?: unknown; tokenType?: unknown; expiresIn?: unknown };
  if(!data || typeof data.accessToken !== 'string' || data.tokenType !== 'Bearer'
      || typeof data.expiresIn !== 'number' || !Number.isFinite(data.expiresIn) || data.expiresIn <= 0) throw new Error('invalid-response');
  let expiresAt = Date.now() + data.expiresIn * 1000;
  try {
    const part=data.accessToken.split('.')[1];
    const claims=JSON.parse(atob(part.replace(/-/g,'+').replace(/_/g,'/')));
    if(typeof claims.exp !== 'number' || !Number.isFinite(claims.exp))throw new Error();
    expiresAt=Math.min(expiresAt,claims.exp*1000);
  } catch { throw new Error('invalid-response'); }
  if(expiresAt <= Date.now())throw new Error('invalid-response');
  // El servidor valida la firma y los permisos. Acá solo se administra la sesión visual.
  sessionStorage.setItem(key,JSON.stringify({accessToken:data.accessToken,expiresAt,email}));
}
export function safeReturn(value: string | null) {
  if(!value || !value.startsWith('/') || value.startsWith('//'))return '/';
  try {
    const url=new URL(value,window.location.origin);
    if(url.origin !== window.location.origin || /^\/(login|registro)\/?$/.test(url.pathname))return '/';
    return url.pathname+url.search+url.hash;
  } catch { return '/'; }
}
