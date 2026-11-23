export const eventTypes = ['RECITAL', 'TEATRO', 'DEPORTE', 'CONFERENCIA', 'FESTIVAL', 'OTRO'] as const;
export const pageSize = 10;

export function searchPath(tipo: string, ciudad: string, pagina: number) {
  if (tipo && !eventTypes.includes(tipo as typeof eventTypes[number])) throw new Error('Tipo de evento inválido.');
  if (!Number.isSafeInteger(pagina) || pagina < 0) throw new Error('Página inválida.');
  const query = new URLSearchParams({ pagina: String(pagina), tamanio: String(pageSize), ordenar: 'fechaHora,asc' });
  if (tipo) query.set('tipo', tipo);
  if (ciudad.trim()) query.set('ciudad', ciudad.trim());
  return `/api/eventos/buscar?${query}`;
}

export function matchesText(event: { title: string; artist: string; venue: string }, query: string) {
  const normalize = (value: string) => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  return normalize([event.title, event.artist, event.venue].join(' ')).includes(normalize(query.trim()));
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
