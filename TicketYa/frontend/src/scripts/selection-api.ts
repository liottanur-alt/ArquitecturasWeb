import { api } from './api';
import { mapCatalog } from './catalog-data';
import { parseAvailability, positiveId, saleOpen, type Availability } from './selection-data';
export async function selectionContext(eventId: number, sectorId?: number) {
  if (!positiveId(eventId) || (sectorId !== undefined && !positiveId(sectorId))) throw new Error('ID de evento o sector inválido.');
  const [detail, inventory] = await Promise.all([
    api('/api/eventos/' + eventId, { cache: 'no-store' }),
    api('/eventos/' + eventId + '/sectores' + (sectorId === undefined ? '' : '/' + sectorId + '/disponibilidad'), { cache: 'no-store' }),
  ]);
  const event = mapCatalog([detail.data])[0];
  if (event.id !== eventId) throw new Error('El servidor devolvió otro evento.');
  const raw = sectorId === undefined ? inventory.data : [inventory.data];
  if (!Array.isArray(raw)) throw new Error('El servidor devolvió sectores inválidos.');
  const sectors: Availability[] = raw.map(value => parseAvailability(value, eventId, sectorId));
  if (new Set(sectors.map(sector => sector.idSector)).size !== sectors.length) throw new Error('El servidor devolvió sectores duplicados.');
  return { event, sectors, open: saleOpen(detail.data) };
}
