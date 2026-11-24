import { api } from './api';

type Turn =
  | { state: 'EN_ESPERA'; position: number | null }
  | { state: 'ADMITIDO'; authorization: string; expiresAt: number }
  | { state: 'VENCIDO' | 'COMPLETADO' };

function parseTurn(value: unknown, eventId: number): Turn {
  const turn = value as Record<string, unknown> | null;
  if (!turn || turn.eventoId !== eventId || typeof turn.turnoId !== 'string' || !turn.turnoId.trim()) {
    throw new Error('El servidor devolvió un turno inválido.');
  }
  if (turn.estado === 'EN_ESPERA' && (turn.posicion === null || Number.isSafeInteger(turn.posicion) && Number(turn.posicion) > 0)) {
    return { state: 'EN_ESPERA', position: turn.posicion as number | null };
  }
  if (turn.estado === 'ADMITIDO' && typeof turn.autorizacionCompra === 'string' && turn.autorizacionCompra.trim()
    && typeof turn.venceEn === 'string') {
    const expiresAt = Date.parse(turn.venceEn);
    if (Number.isFinite(expiresAt)) {
      return expiresAt > Date.now() ? { state: 'ADMITIDO', authorization: turn.autorizacionCompra, expiresAt } : { state: 'VENCIDO' };
    }
  }
  if (turn.estado === 'VENCIDO' || turn.estado === 'COMPLETADO') return { state: turn.estado };
  throw new Error('El servidor devolvió un turno inválido.');
}

export function initQueue(
  eventId: number,
  panel: HTMLElement,
  join: HTMLButtonElement,
  status: HTMLElement,
  isActive: () => boolean,
  refreshCheckout: () => void
) {
  const endpoint = '/api/eventos/' + eventId + '/fila';
  let turn: Turn | null = null;
  let request: AbortController | null = null;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let error = '';

  function stop() {
    clearTimeout(timer);
    request?.abort();
    request = null;
    turn = null;
    error = '';
  }

  function refresh() {
    const active = isActive();
    panel.hidden = !active;
    if (!active) {
      stop();
      status.textContent = '';
      return;
    }
    join.disabled = !!request || turn?.state === 'EN_ESPERA' || turn?.state === 'ADMITIDO';
    let message = error || 'Entrá a la fila virtual para habilitar la compra.';
    if (turn?.state === 'EN_ESPERA') {
      message = turn.position === null ? 'Estás en espera.' : 'Estás en espera. Posición ' + turn.position + '.';
    } else if (turn?.state === 'ADMITIDO') {
      const expiration = new Date(turn.expiresAt).toLocaleTimeString('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' });
      message = 'Turno admitido. Podés iniciar la compra hasta las ' + expiration + ' (Buenos Aires).';
    } else if (turn?.state === 'VENCIDO') {
      message = 'Tu turno venció. Entrá de nuevo a la fila.';
    } else if (turn?.state === 'COMPLETADO') {
      message = 'Tu turno ya se completó. Entrá de nuevo a la fila para otra compra.';
    } else if (request) {
      message = 'Buscando tu turno…';
    }
    if (status.textContent !== message) status.textContent = message;
  }

  async function consult(method: 'GET' | 'POST') {
    if (!isActive() || request) return;
    const pending = new AbortController();
    request = pending;
    error = '';
    refreshCheckout();
    try {
      const { data } = await api(method === 'POST' ? endpoint : endpoint + '/mi-turno', {
        method, cache: 'no-store', signal: AbortSignal.any([pending.signal, AbortSignal.timeout(12000)])
      });
      if (request !== pending || !isActive()) return;
      turn = parseTurn(data, eventId);
      if (turn.state === 'EN_ESPERA') timer = setTimeout(() => { void consult('GET'); }, 3000);
      if (turn.state === 'ADMITIDO') {
        timer = setTimeout(() => { turn = { state: 'VENCIDO' }; refreshCheckout(); }, Math.max(0, turn.expiresAt - Date.now()));
      }
    } catch (cause) {
      if (request !== pending || !isActive()) return;
      turn = null;
      const message = cause instanceof Error ? cause.message : 'No pudimos consultar el turno.';
      error = message + ' Volvé a entrar a la fila para recuperar tu turno.';
    } finally {
      if (request === pending) {
        request = null;
        refreshCheckout();
      }
    }
  }

  join.addEventListener('click', () => { if (!join.disabled) void consult('POST'); });
  return {
    refresh,
    authorization() {
      return isActive() && turn?.state === 'ADMITIDO' && turn.expiresAt > Date.now() ? turn.authorization : undefined;
    }
  };
}
