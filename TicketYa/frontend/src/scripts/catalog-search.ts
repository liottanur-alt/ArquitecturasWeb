export const eventTypes = ['RECITAL', 'TEATRO', 'DEPORTE', 'CONFERENCIA', 'FESTIVAL', 'OTRO'] as const;
export const pageSize = 10;

export function searchPath(tipo: string, ciudad: string, pagina: number, texto = '', dates?: { from: string; to?: string }) {
  if (tipo && !eventTypes.includes(tipo as typeof eventTypes[number])) throw new Error('Tipo de evento inválido.');
  if (!Number.isSafeInteger(pagina) || pagina < 0) throw new Error('Página inválida.');
  const term = texto.trim();
  const query = new URLSearchParams({ pagina: String(pagina), tamanio: String(pageSize), ordenar: term ? 'relevancia,desc' : 'fechaHora,asc' });
  if (tipo) query.set('tipo', tipo);
  if (ciudad.trim()) query.set('ciudad', ciudad.trim());
  if (term) query.set('q', term);
  if (dates) {
    query.set('fechaDesde', dates.from);
    if (dates.to) query.set('fechaHasta', dates.to);
  }
  return `/api/eventos/buscar?${query}`;
}

const argentina = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'America/Argentina/Buenos_Aires', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', second: '2-digit',
});

export function catalogWindow(filter: string, now = new Date()) {
  const local = argentina.format(now).replace(' ', 'T');
  const today = local.slice(0, 10);
  const day = (date: string, offset: number) => {
    const value = new Date(date + 'T00:00:00Z');
    value.setUTCDate(value.getUTCDate() + offset);
    return value.toISOString().slice(0, 10);
  };
  if (filter === 'today') return { from: local, to: today + 'T23:59:59.999999' };
  if (filter === 'weekend') {
    const weekday = new Date(today + 'T00:00:00Z').getUTCDay();
    const saturday = day(today, weekday === 0 ? -1 : 6 - weekday);
    return { from: [local, saturday + 'T00:00:00'].sort()[1], to: day(saturday, 1) + 'T23:59:59.999999' };
  }
  if (/^\d{4}-\d{2}-\d{2}$/.test(filter)) {
    return { from: filter === today ? local : filter + 'T00:00:00', to: filter + 'T23:59:59.999999' };
  }
  return { from: local };
}

/** El contador protege incluso si el transporte ignora AbortSignal. */
export function latestRequest() {
  let version = 0;
  let controller: AbortController | undefined;
  return async function run<T>(request: (signal: AbortSignal) => Promise<T>) {
    const current = ++version;
    controller?.abort();
    controller = new AbortController();
    // El cliente respeta el signal recibido; conservar su timeout de 12 segundos.
    const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(12000)]);
    try {
      const data = await request(signal);
      return current === version ? { current: true as const, data } : { current: false as const };
    } catch (cause) {
      if (current !== version) return { current: false as const };
      throw cause;
    }
  };
}
