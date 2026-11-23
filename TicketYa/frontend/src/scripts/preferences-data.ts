export const preferredTypes = ['RECITAL','TEATRO','DEPORTE','CONFERENCIA','FESTIVAL','OTRO'] as const;
export type Preferences = { tiposEventoPreferidos: string[]; ciudadPreferida: string | null; artistasFavoritos: number[] };
export function preferencesBody(types: string[], city: string) {
  if (!Array.isArray(types) || !types.every(type => preferredTypes.includes(type as typeof preferredTypes[number]))) throw new Error('Tipo de evento inválido.');
  const value = city.trim(); if (value.length > 100) throw new Error('La ciudad preferida admite hasta 100 caracteres.');
  return { tiposEventoPreferidos: [...new Set(types)], ciudadPreferida: value || null };
}
export function parsePreferences(value: unknown): Preferences {
  const p = value as Preferences;
  if (!p || !Array.isArray(p.tiposEventoPreferidos) || !p.tiposEventoPreferidos.every(type => preferredTypes.includes(type as typeof preferredTypes[number]))
    || p.ciudadPreferida !== null && typeof p.ciudadPreferida !== 'string' || !Array.isArray(p.artistasFavoritos)
    || !p.artistasFavoritos.every(id => Number.isSafeInteger(id) && id > 0)) throw new Error('Preferencias recibidas inválidas.');
  return { tiposEventoPreferidos: [...new Set(p.tiposEventoPreferidos)], ciudadPreferida: p.ciudadPreferida, artistasFavoritos: [...new Set(p.artistasFavoritos)] };
}
export function parseArtists(value: unknown, favorite = false) {
  if (!Array.isArray(value)) throw new Error('La lista de artistas recibida no es válida.');
  const result = value.map(artist => {
    const id = favorite ? artist?.idArtista : artist?.id;
    if (!Number.isSafeInteger(id) || id <= 0 || typeof artist.nombre !== 'string' || !artist.nombre.trim()) throw new Error('Se recibió un artista inválido.');
    return { id: id as number, nombre: artist.nombre as string, genero: typeof artist.genero === 'string' ? artist.genero : '' };
  });
  if (new Set(result.map(a => a.id)).size !== result.length) throw new Error('Se recibieron artistas duplicados.');
  return result;
}
