const categories: Record<string, string> = {
  RECITAL: 'Recitales', FESTIVAL: 'Festivales', TEATRO: 'Teatro', DEPORTE: 'Deportes', CONFERENCIA: 'Conferencias', OTRO: 'Otros',
};
const states: Record<string, string> = { EN_CURSO: 'En curso', AGOTADO: 'Agotado', CANCELADO: 'Cancelado', FINALIZADO: 'Finalizado' };
const text = (value: unknown, fallback: string) => typeof value === 'string' && value.trim() ? value.trim() : fallback;
const object = (value: unknown): Record<string, unknown> => value && typeof value === 'object' ? value as Record<string, unknown> : {};

/** fechaHora es LocalDateTime de Buenos Aires: conservar sus componentes, sin depender de la zona del navegador. */
export function catalogDate(value: unknown) {
  const empty = { day: '—', month: 'SIN FECHA', monthYear: 'SIN FECHA', fullDate: 'Fecha no informada' };
  if (typeof value !== 'string') return empty;
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?$/.exec(value);
  if (!match) return empty;
  const [year, month, day, hour, minute, second] = match.slice(1).map(part => Number(part ?? 0));
  const date = new Date(Date.UTC(year, month - 1, day, hour, minute, Number(second)));
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day
    || date.getUTCHours() !== hour || date.getUTCMinutes() !== minute || date.getUTCSeconds() !== Number(second)) return empty;
  const shortMonth = new Intl.DateTimeFormat('es-AR', { month: 'short', timeZone: 'UTC' }).format(date).replace('.', '').toUpperCase();
  return { day: String(day).padStart(2, '0'), month: shortMonth, monthYear: `${shortMonth} / ${year}`,
    fullDate: new Intl.DateTimeFormat('es-AR', { dateStyle: 'long', timeStyle: 'short', hourCycle: 'h23', timeZone: 'UTC' }).format(date) + ' · hora de Buenos Aires' };
}

export function mapCatalog(data: unknown) {
  if (!Array.isArray(data)) throw new Error('El servidor devolvió un catálogo inválido.');
  const ids = new Set<number>();
  return data.map(raw => {
    const event = object(raw);
    if (!Number.isSafeInteger(event.id) || Number(event.id) <= 0 || ids.has(Number(event.id))) throw new Error('El catálogo contiene un ID de evento inválido o duplicado.');
    const id = Number(event.id); ids.add(id);
    const place = object(event.lugar);
    const artists = Array.isArray(event.participantes) ? event.participantes.map(person => text(object(person).nombre, '')).filter(Boolean).join(' · ') : '';
    const venue = text(place.nombre, 'Lugar no informado'), city = text(place.ciudad, '');
    return {
      id, title: text(event.nombre, 'Evento sin nombre'), description: text(event.descripcion, 'Descripción no informada'),
      category: categories[String(event.tipo)] || 'Tipo no informado', artist: artists || 'Participantes no informados',
      venue, city, location: [venue, city].filter(Boolean).join(' · '), address: text(place.direccion, ''),
      state: states[String(event.estado)] || 'Estado no informado', high: event.demanda === true,
      image: '/event-placeholder.svg', ...catalogDate(event.fechaHora),
    };
  });
}
export type CatalogEvent = ReturnType<typeof mapCatalog>[number];
