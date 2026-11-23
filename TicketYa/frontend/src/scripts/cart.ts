import { getSelectedProfileId } from './session';
import { normalizeDraft, type Draft } from './selection-data';
export { normalizeDraft } from './selection-data';
export type { Draft } from './selection-data';
// Versión nueva: los borradores de ejemplo con sectores de texto no se importan.
function key(profileId = getSelectedProfileId()) {
  if (profileId === null) throw new Error('Elegí un perfil de demostración para guardar la selección.');
  return 'ticketya-carrito-v2-perfil-' + profileId;
}
export function getCart(): Draft[] {
  if (getSelectedProfileId() === null) return [];
  const raw: unknown = JSON.parse(localStorage.getItem(key()) || '[]');
  if (!Array.isArray(raw)) throw new Error('El carrito local no tiene un formato válido.');
  const used = new Set<string>();
  return raw.slice(0, 100).map(normalizeDraft).filter((value): value is Draft => {
    if (!value) return false;
    const id = value.eventId + ':' + value.sectorId;
    if (used.has(id)) return false; used.add(id); return true;
  });
}
export function saveDraft(value: Draft, expectedProfileId = getSelectedProfileId()) {
  if (expectedProfileId === null || getSelectedProfileId() !== expectedProfileId) throw new Error('El perfil cambió. Volvé a revisar la selección.');
  const draft = normalizeDraft(value); if (!draft) throw new Error('Selección inválida.');
  const items = getCart().filter(item => item.eventId !== draft.eventId || item.sectorId !== draft.sectorId);
  if (items.length >= 100) throw new Error('El carrito está lleno. Quitá una selección antes de guardar otra.');
  localStorage.setItem(key(expectedProfileId), JSON.stringify([draft, ...items]));
}
export function removeDraft(eventId: number, sectorId: number) {
  localStorage.setItem(key(), JSON.stringify(getCart().filter(item => item.eventId !== eventId || item.sectorId !== sectorId)));
}
export function cartLink() { return '/perfil/#carrito'; }
