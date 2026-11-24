import { clearSession, getSession, safeReturn } from './session.ts';

/** Base de transporte; las rutas recibidas conservan el contrato del Gateway. */
const baseURL = (import.meta.env?.PUBLIC_API_BASE_URL || '/gateway').replace(/\/+$/, '');

export class ApiError extends Error {
  readonly status: number;
  readonly payload: unknown;
  readonly correlationId: string | null;
  readonly retryAfter: string | null;

  constructor(response: Response, payload: unknown) {
    const object = payload && typeof payload === 'object' ? payload as Record<string, unknown> : {};
    const message = typeof payload === 'string' ? payload :
      [object.detail, object.error, object.message, object.detalle, object.title]
        .find((value): value is string => typeof value === 'string' && value.trim().length > 0);
    super(message || (response.status === 403 ? 'No tenés permisos para esta operación.' : `HTTP ${response.status}`));
    this.name = 'ApiError';
    this.status = response.status;
    this.payload = payload;
    this.correlationId = response.headers.get('X-Correlation-Id') ??
      (typeof object.correlationId === 'string' ? object.correlationId : null);
    this.retryAfter = response.headers.get('Retry-After');
  }
}

export class ApiConnectionError extends Error {
  readonly reason: unknown;
  constructor(reason: unknown) {
    super(reason instanceof Error && ['AbortError', 'TimeoutError'].includes(reason.name)
      ? 'La solicitud fue cancelada o superó el tiempo de espera. No se pudo confirmar el resultado.'
      : 'No se pudo leer la respuesta del Gateway. Revisá la conexión, el proxy o CORS. No se pudo confirmar el resultado.');
    this.name = 'ApiConnectionError';
    this.reason = reason;
  }
}

type ApiOptions = Omit<RequestInit, 'body' | 'credentials'> & { body?: unknown };

/** Envía la sesión de la pestaña al Gateway y conserva el resultado de cada operación. */
export async function api<T = unknown>(path: string, { body, headers, signal, ...options }: ApiOptions = {}) {
  if (!path.startsWith('/') || path.startsWith('//') || path.includes('\\') || /[\r\n#]/.test(path)
    || path.split('?')[0].split('/').some(segment => ['.', '..'].includes(decodeURIComponent(segment)))) {
    throw new Error('La ruta de API debe ser una ruta absoluta del Gateway.');
  }
  const requestHeaders = new Headers(headers);
  requestHeaders.set('Accept', 'application/json');
  const endpoint = path.split('?')[0];
  const isPublic = endpoint === '/auth/login' || endpoint === '/auth/registro' || endpoint === '/actuator/health';
  const session = isPublic ? null : getSession();
  requestHeaders.delete('Authorization');
  if (session) requestHeaders.set('Authorization', 'Bearer ' + session.accessToken);
  if (body !== undefined) requestHeaders.set('Content-Type', 'application/json');
  const requestBody = body === undefined ? undefined : JSON.stringify(body);
  let response: Response;
  let text: string;
  try {
    response = await fetch(`${baseURL}${path}`, {
      ...options, headers: requestHeaders, body: requestBody,
      signal: signal ?? AbortSignal.timeout(12000), credentials: 'omit'
    });
    text = response.status === 204 ? '' : await response.text();
  } catch (cause) { throw new ApiConnectionError(cause); }
  let data: unknown = text || null;
  if (text) { try { data = JSON.parse(text); } catch { /* También se admite texto de errores del proxy. */ } }
  if (response.status === 401 && !isPublic && typeof window !== 'undefined') {
    const current = getSession();
    if (!current || current.accessToken === session?.accessToken) {
      if (current) clearSession();
      const { pathname, search, hash } = window.location;
      const returnTo = safeReturn(pathname + search + hash);
      if (!/^\/(login|registro)\/?$/.test(pathname)) {
        window.location.replace('/login/?volver=' + encodeURIComponent(returnTo));
      }
    }
  }
  if (!response.ok) throw new ApiError(response, data);
  return { data: data as T | null, status: response.status, headers: response.headers };
}
