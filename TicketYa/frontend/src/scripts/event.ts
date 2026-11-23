import { initPurchase } from './purchase-ui';
import { selectionContext } from './selection-api';
import { getCart, saveDraft } from './cart';
import { getSelectedProfileId } from './session';
import { eventLink, positiveId, reconcileDraft, type Draft, type Availability } from './selection-data';

const params = new URLSearchParams(location.search);
const eventId = Number(params.get('id'));
const requestedSector = params.has('sector') ? Number(params.get('sector')) : undefined;
const owner = getSelectedProfileId();
const byId = <T extends HTMLElement = HTMLElement>(id: string) => document.getElementById(id) as T;
const dialog = byId<HTMLDialogElement>('review-dialog');
const quantity = byId<HTMLInputElement>('quantity');
const review = byId<HTMLButtonElement>('review-selection');
const save = byId<HTMLButtonElement>('save-cart');
const message = byId('selection-message');
const amount = (value: number) => value.toLocaleString('es-AR', { maximumFractionDigits: 2 });
let context: Awaited<ReturnType<typeof selectionContext>> | null = null;
let selectedSector: number | undefined;
let draft: Draft | null = null;
let busy = false, restored = false;
let purchaseUI: ReturnType<typeof initPurchase> | undefined;
const sector = () => context?.sectors.find(value => value.idSector === selectedSector);
function newDraft(current: Availability, count: number, ids: number[] = []): Draft | null {
  return count ? { eventId, sectorId: current.idSector, type: current.tipo, quantity: count, seatIds: ids, updatedAt: new Date().toISOString() } : null;
}
function summary() {
  const current = sector();
  const count = draft?.quantity || 0;
  const text = !draft ? 'Elegí una cantidad o asientos de un sector.' : 'Sector #' + draft.sectorId + ' · ' + count + ' entradas' + (draft.type === 'NUMERADO' ? ' · IDs de asientos: ' + draft.seatIds.join(', ') : ' · CAMPO');
  byId('summary-items').textContent = text;
  byId('total').textContent = current && count ? amount(current.precio * count) : '—';
  byId('review-items').textContent = text + (current && count ? ' · Importe consultado: ' + amount(current.precio * count) : '');
  review.disabled = busy || !context?.open || !draft || !current || current.cancelado;
  save.disabled = review.disabled || owner === null;
  byId('choose-profile').hidden = owner !== null;
  byId<HTMLAnchorElement>('choose-profile').href = '/login/?volver=' + encodeURIComponent(eventLink(eventId, selectedSector));
  byId('view-cart').hidden = true;
  purchaseUI?.refresh();
}
function paintSector() {
  const current = sector();
  byId('sector-panel').hidden = !current;
  document.querySelectorAll<HTMLButtonElement>('[data-sector]').forEach(button => {
    const active = Number(button.dataset.sector) === selectedSector; button.classList.toggle('selected', active); button.setAttribute('aria-pressed', String(active));
  });
  if (!current) { summary(); return; }
  byId('sector-title').textContent = 'Sector #' + current.idSector;
  byId('sector-kind').textContent = current.tipo === 'CAMPO' ? 'Ubicación libre · CAMPO' : 'Asientos numerados · NUMERADO';
  byId('sector-availability').textContent = current.cancelado ? 'Inventario cancelado' : current.disponibles + ' disponibles al consultar';
  byId('campo-selector').hidden = current.tipo !== 'CAMPO'; byId('numbered-selector').hidden = current.tipo !== 'NUMERADO';
  quantity.value = String(draft?.quantity || 0); quantity.max = String(Math.min(current.disponibles, 1000));
  quantity.disabled = busy || !context?.open || current.cancelado || current.disponibles === 0;
  const seats = byId('seat-list'); seats.replaceChildren();
  current.asientos.forEach(seat => {
    const button = document.createElement('button'); button.className = 'real-seat'; button.dataset.seat = String(seat.idAsiento);
    button.textContent = 'Fila ' + seat.fila + ' · ' + seat.columna + ' · #' + seat.idAsiento;
    button.setAttribute('aria-pressed', String(!!draft?.seatIds.includes(seat.idAsiento)));
    button.disabled = busy || !context?.open || current.cancelado || seat.estado !== 'DISPONIBLE';
    button.title = seat.estado; seats.append(button);
  });
  summary();
}
function paint() {
  if (!context) return;
  document.title = context.event.title + ' — TicketYa';
  const bindings = { 'event-title': context.event.title, 'event-breadcrumb': context.event.title, 'event-category': context.event.category,
    'event-artists': context.event.artist, 'event-date': context.event.fullDate, 'event-location': context.event.location,
    'event-description': context.event.description, 'summary-event': context.event.title };
  for (const [id, value] of Object.entries(bindings)) byId(id).textContent = value;
  byId('event-content').hidden = false; byId('event-state').hidden = true;
  byId('availability-status').textContent = !context.open ? 'Este evento no admite selección para compra por su estado o fecha.'
    : !context.sectors.length ? 'Este evento no tiene sectores configurados.' : 'Disponibilidad consultada. Guardar no reserva lugares.';
  const list = byId('sector-list'); list.replaceChildren();
  context.sectors.forEach(current => {
    const button = document.createElement('button'); button.className = 'sector-option'; button.dataset.sector = String(current.idSector);
    const name = document.createElement('span'); name.className = 'sector-name'; name.textContent = 'Sector #' + current.idSector + ' · ' + current.tipo;
    const price = document.createElement('span'); price.className = 'sector-price'; price.textContent = amount(current.precio) + ' por entrada';
    button.disabled = busy; button.append(name, price); list.append(button);
  });
  paintSector();
}
async function load() {
  if (busy) return; busy = true; review.disabled = true; save.disabled = true;
  byId('event-state').hidden = false; byId('event-state').setAttribute('aria-busy', 'true'); byId('event-content').hidden = true;
  byId('retry-event').hidden = true; byId('event-error').textContent = ''; byId('event-state-title').textContent = 'Cargando evento y disponibilidad…';
  try {
    if (!positiveId(eventId) || (requestedSector !== undefined && !positiveId(requestedSector))) throw new Error('El enlace no contiene un ID válido. Volvé al catálogo.');
    const previous = draft;
    context = await selectionContext(eventId);
    selectedSector = selectedSector ?? requestedSector ?? context.sectors[0]?.idSector;
    let saved = previous;
    if (!saved && !restored && params.get('carrito') === '1') {
      try { saved = getCart().find(item => item.eventId === eventId && item.sectorId === selectedSector) || null; }
      catch (cause) { message.textContent = cause instanceof Error ? cause.message : 'No pudimos leer el carrito.'; }
    }
    restored = true;
    const available = sector();
    if (saved) {
      const result = context.open && available ? reconcileDraft(saved, available) : { draft: null, changed: true };
      draft = result.draft;
      message.textContent = result.changed ? 'La disponibilidad cambió: quitamos asientos o cantidades no disponibles. Revisá y guardá nuevamente la selección.' : 'Selección recuperada y contrastada con disponibilidad actual. Guardar no reserva lugares.';
    } else draft = null;
    busy = false; paint();
  } catch (cause) {
    context = null; draft = null; byId('event-state-title').textContent = 'No pudimos consultar el evento.';
    byId('event-error').textContent = cause instanceof Error ? cause.message : 'Revisá la conexión.'; byId('retry-event').hidden = false;
  } finally { busy = false; byId('event-state').setAttribute('aria-busy', 'false'); }
}
byId('retry-event').addEventListener('click', load);
byId('refresh-availability').addEventListener('click', () => { dialog.close(); load(); });
byId('sector-list').addEventListener('click', event => {
  const button = (event.target as Element).closest<HTMLElement>('[data-sector]'); if (!button || busy) return;
  if (selectedSector !== Number(button.dataset.sector)) { selectedSector = Number(button.dataset.sector); draft = null; message.textContent = 'Nuevo sector: se limpió la selección sin guardar. Cada selección corresponde a una compra por sector.'; }
  paintSector();
});
quantity.addEventListener('input', () => {
  const current = sector(); if (!current || busy || quantity.disabled) return;
  const count = Number(quantity.value);
  if (!Number.isInteger(count) || count < 0 || count > Math.min(current.disponibles, 1000)) { draft = null; message.textContent = 'Ingresá una cantidad entera dentro del cupo consultado.'; summary(); return; }
  draft = newDraft(current, count); message.textContent = ''; summary();
});
byId('seat-list').addEventListener('click', event => {
  const button = (event.target as Element).closest<HTMLButtonElement>('[data-seat]'), current = sector();
  if (!button || button.disabled || !current || busy) return;
  const id = Number(button.dataset.seat), ids = draft?.seatIds || [];
  const values = ids.includes(id) ? ids.filter(value => value !== id) : [...ids, id];
  if (values.length > Math.min(current.disponibles, 1000)) { message.textContent = 'La selección supera el cupo consultado.'; return; }
  draft = newDraft(current, values.length, values); message.textContent = ''; paintSector();
});
byId('clear-selection').addEventListener('click', () => { draft = null; message.textContent = 'Selección vacía.'; paintSector(); });
review.addEventListener('click', () => { if (!review.disabled) { byId('save-cart-status').textContent = ''; dialog.showModal(); } });
byId('close-review').addEventListener('click', () => dialog.close());
save.addEventListener('click', async () => {
  if (!draft || busy || owner === null || getSelectedProfileId() !== owner) return;
  const selection = draft, previousPrice = sector()?.precio;
  busy = true; save.disabled = true; review.disabled = true; byId('save-cart-status').textContent = 'Revalidando disponibilidad antes de guardar…';
  try {
    const fresh = await selectionContext(eventId, selection.sectorId), current = fresh.sectors[0];
    const result = fresh.open ? reconcileDraft(selection, current) : { draft: null, changed: true };
    context = { ...fresh, sectors: context!.sectors.map(value => value.idSector === current.idSector ? current : value) };
    draft = result.draft;
    if (result.changed || previousPrice !== current.precio) {
      dialog.close(); message.textContent = 'Cambió el cupo, un asiento, el estado o el precio. Revisá la selección actualizada antes de guardarla.';
    } else {
      saveDraft({ ...selection, updatedAt: new Date().toISOString() }, owner);
      byId('save-cart-status').textContent = 'Selección guardada solo en este navegador. No se reservaron lugares ni se creó una compra.';
    }
  } catch (cause) { byId('save-cart-status').textContent = cause instanceof Error ? cause.message : 'No pudimos verificar ni guardar la selección.'; }
  finally { busy = false; paintSector(); if (dialog.open) byId('view-cart').hidden = !byId('save-cart-status').textContent?.startsWith('Selección guardada'); }
});
window.addEventListener('ticketya:profile', () => location.reload());
window.addEventListener('pageshow', event => { if (getSelectedProfileId() !== owner) location.reload(); else if (event.persisted) { dialog.close(); load(); } });
purchaseUI = initPurchase(owner, eventId, () => !busy && context?.open && !review.disabled ? draft : null, () => { dialog.close(); load(); });
load();
