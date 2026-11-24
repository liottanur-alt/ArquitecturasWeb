import { api, ApiError } from './api';
import { mapCatalog } from './catalog-data';
import { parseAvailability, positiveId, saleOpen, type Availability } from './selection-data';
export async function selectionContext(eventId: number, sectorId?: number) {
  if (!positiveId(eventId) || (sectorId !== undefined && !positiveId(sectorId))) throw new Error('ID de evento o sector inválido.');
  const [detail, inventory] = await Promise.allSettled([
    api('/api/eventos/' + eventId, { cache: 'no-store' }),
    api('/eventos/' + eventId + '/sectores' + (sectorId === undefined ? '' : '/' + sectorId + '/disponibilidad'), { cache: 'no-store' }),
  ]);
  if (detail.status === 'rejected') throw detail.reason;
  const event = mapCatalog([detail.value.data])[0];
  if (event.id !== eventId) throw new Error('El servidor devolvió otro evento.');
  if (inventory.status === 'rejected') {
    if (sectorId === undefined && inventory.reason instanceof ApiError && inventory.reason.status === 404) {
      return { event, sectors: [], open: saleOpen(detail.value.data) };
    }
    throw inventory.reason;
  }
  const raw = sectorId === undefined ? inventory.value.data : [inventory.value.data];
  if (!Array.isArray(raw)) throw new Error('El servidor devolvió sectores inválidos.');
  const sectors: Availability[] = raw.map(value => parseAvailability(value, eventId, sectorId));
  if (new Set(sectors.map(sector => sector.idSector)).size !== sectors.length) throw new Error('El servidor devolvió sectores duplicados.');
  return { event, sectors, open: saleOpen(detail.value.data) };
}
