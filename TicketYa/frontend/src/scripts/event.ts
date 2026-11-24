import { initPurchase } from './purchase-ui';
import { api, ApiError } from './api';
import { selectionContext } from './selection-api';
import { getCart, saveDraft } from './cart';
import { getAuthenticatedUserId } from './session';
import { setEventImage } from './catalog-data';
import { eventLink, positiveId, reconcileDraft, type Draft, type Availability } from './selection-data';

const params = new URLSearchParams(location.search);
const eventId = Number(params.get('id'));
const requestedSector = params.has('sector') ? Number(params.get('sector')) : undefined;
const owner = getAuthenticatedUserId();
const isCurrentOwner = () => owner !== null && getAuthenticatedUserId() === owner;
const byId = <T extends HTMLElement = HTMLElement>(id: string) => document.getElementById(id) as T;
const dialog = byId<HTMLDialogElement>('review-dialog');
const quantity = byId<HTMLInputElement>('quantity');
const subtractQuantity = byId<HTMLButtonElement>('subtract-quantity');
const addQuantity = byId<HTMLButtonElement>('add-quantity');
const review = byId<HTMLButtonElement>('review-selection');
const save = byId<HTMLButtonElement>('save-cart');
const message = byId('selection-message');
const currency = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', minimumFractionDigits: 0, maximumFractionDigits: 2 });
const amount = (value: number) => currency.format(value);
const sellable = (value: Availability) => !value.cancelado && value.disponibles > 0;
const sectorType = (value: Availability) => value.tipo === 'CAMPO' ? 'Ubicación libre' : 'Asientos numerados';
let context: Awaited<ReturnType<typeof selectionContext>> | null = null;
let selectedSector: number | undefined;
let draft: Draft | null = null;
let busy = false, restored = false;
let purchaseUI: ReturnType<typeof initPurchase> | undefined;
const sectorNames = new Map<number, string>();
const sectorUpdates = new Map<number, { text: string; seats: Set<number> }>();
const sectorName = (id: number) => sectorNames.get(id) ?? 'Sector #' + id;
const sector = () => context?.sectors.find(value => value.idSector === selectedSector);
function newDraft(current: Availability, count: number, ids: number[] = []): Draft | null {
  return count ? { eventId, sectorId: current.idSector, type: current.tipo, quantity: count, seatIds: ids, updatedAt: new Date().toISOString() } : null;
}
function summary() {
  const current = sector();
  const count = draft?.quantity || 0;
  const selectedSeats = current?.asientos.filter(seat => draft?.seatIds.includes(seat.idAsiento))
    .map(seat => 'Fila ' + seat.fila + ', asiento ' + seat.columna) ?? [];
  const text = !draft ? 'Elegí un sector y agregá tus entradas.'
    : [count === 1 ? '1 entrada' : count + ' entradas', sectorName(draft.sectorId),
      selectedSeats.length ? selectedSeats.join(' · ') : 'Ubicación libre'].join(' · ');
  const total = current && count ? amount(current.precio * count) : '—';
  byId('summary-items').textContent = text;
  byId('total').textContent = total;
  byId('review-items').textContent = text;
  byId('review-event').textContent = context?.event.title ?? '';
  byId('review-total').textContent = total;
  byId('seat-selection-caption').textContent = selectedSeats.length ? 'Elegidos: ' + selectedSeats.join(' · ') : 'Elegí tus asientos en el mapa.';
  const canAdjust = !busy && !!context?.open && !quantity.disabled && current?.tipo === 'CAMPO';
  subtractQuantity.disabled = !canAdjust || count === 0;
  addQuantity.disabled = !canAdjust || count >= Number(quantity.max);
  review.disabled = busy || !context?.open || !draft || !current || current.cancelado;
  const mobileReview = byId<HTMLButtonElement>('mobile-review-selection');
  const canReview = !review.disabled;
  mobileReview.hidden = !canReview;
  mobileReview.disabled = !canReview;
  byId('jump-to-sectors').hidden = canReview;
  byId('mobile-from-price').textContent = canReview
    ? (count === 1 ? '1 entrada' : count + ' entradas') + ' · ' + total
    : byId('event-from-price').textContent;
  byId<HTMLButtonElement>('clear-selection').disabled = busy || !draft;
  save.disabled = review.disabled || owner === null;
  byId('choose-profile').hidden = owner !== null;
  byId<HTMLAnchorElement>('choose-profile').href = '/login/?volver=' + encodeURIComponent(eventLink(eventId, selectedSector));
  purchaseUI?.refresh();
}
function paintSector() {
  const current = sector();
  byId('sector-panel').hidden = !current;
  document.querySelectorAll<HTMLButtonElement>('[data-sector]').forEach(button => {
    const active = Number(button.dataset.sector) === selectedSector; button.classList.toggle('selected', active); button.setAttribute('aria-pressed', String(active));
  });
  if (!current) { summary(); return; }
  byId('sector-title').textContent = sectorName(current.idSector);
  byId('sector-kind').textContent = sectorType(current);
  const change = sectorUpdates.get(current.idSector);
  const update = byId('sector-update');
  update.textContent = change?.text ?? '';
  update.hidden = !change;
  const availability = byId('sector-availability');
  availability.textContent = current.cancelado ? 'Sector cancelado' : current.disponibles === 0 ? 'Agotado' : current.disponibles + ' disponibles';
  availability.dataset.tone = !sellable(current) ? 'closed' : current.disponibles <= 10 ? 'limited' : 'available';
  byId('campo-selector').hidden = current.tipo !== 'CAMPO'; byId('numbered-selector').hidden = current.tipo !== 'NUMERADO';
  quantity.value = String(draft?.quantity || 0); quantity.max = String(Math.min(current.disponibles, 1000));
  quantity.removeAttribute('aria-invalid');
  quantity.disabled = busy || !context?.open || current.cancelado || current.disponibles === 0;
  const seats = byId('seat-list'); seats.replaceChildren();
  const rows = new Map<string, HTMLElement>();
  const compare = new Intl.Collator('es-AR', { numeric: true });
  [...current.asientos].sort((a, b) => compare.compare(a.fila, b.fila) || compare.compare(a.columna, b.columna)).forEach(seat => {
    let row = rows.get(seat.fila);
    if (!row) {
      row = document.createElement('div'); row.className = 'seat-map-row';
      const label = document.createElement('span'); label.className = 'seat-map-row-label'; label.textContent = 'Fila ' + seat.fila;
      row.append(label); rows.set(seat.fila, row); seats.append(row);
    }
    const button = document.createElement('button'); button.className = 'seat-cell'; button.dataset.seat = String(seat.idAsiento);
    button.textContent = seat.columna;
    const selected = !!draft?.seatIds.includes(seat.idAsiento);
    button.setAttribute('aria-pressed', String(selected));
    button.disabled = busy || !context?.open || current.cancelado || seat.estado !== 'DISPONIBLE';
    button.dataset.seatTone = selected ? 'selected' : seat.estado === 'VENDIDO' ? 'sold' : button.disabled ? 'unavailable' : 'available';
    const state = { selected: 'Seleccionado', sold: 'Vendido', unavailable: 'No disponible', available: 'Disponible' }[button.dataset.seatTone];
    if (change?.seats.has(seat.idAsiento)) button.dataset.stockChanged = '';
    button.title = 'Fila ' + seat.fila + ' · Asiento ' + seat.columna + ' · ' + sectorName(current.idSector) + ' · ' + amount(current.precio) + ' · ' + state;
    button.setAttribute('aria-label', button.title);
    row.append(button);
  });
  summary();
}
function paint() {
  if (!context) return;
  document.title = context.event.title + ' — TicketYa';
  setEventImage(byId<HTMLImageElement>('event-image'), context.event.image);
  setEventImage(byId<HTMLImageElement>('event-backdrop'), context.event.image);
  const location = [context.event.venue, context.event.address, context.event.city].filter(Boolean).join(' · ');
  const bindings = { 'event-title': context.event.title, 'event-breadcrumb': context.event.title, 'event-category': context.event.category,
    'event-artists': context.event.artist, 'event-date': context.event.fullDate, 'event-location': context.event.location,
    'event-description': context.event.description, 'event-fact-date': context.event.fullDate, 'event-address': location };
  for (const [id, value] of Object.entries(bindings)) byId(id).textContent = value;
  byId('event-artists').hidden = context.event.artist === 'Participantes no informados';
  const map = byId<HTMLAnchorElement>('event-map');
  map.hidden = !context.event.address && (!context.event.city || context.event.venue === 'Lugar no informado');
  if (!map.hidden) map.href = 'https://www.google.com/maps/search/?' + new URLSearchParams({ api: '1', query: location });
  byId('event-content').hidden = false; byId('event-state').hidden = true;
  const available = context.open ? context.sectors.filter(sellable) : [];
  const count = available.reduce((total, value) => total + value.disponibles, 0);
  const status = byId('event-availability');
  status.textContent = !context.open ? (context.event.state === 'En curso' ? 'Venta cerrada' : context.event.state)
    : !context.sectors.length ? 'Entradas sin publicar'
    : count === 0 ? 'Agotado' : count <= 10 ? 'Últimas entradas' : 'Entradas disponibles';
  status.dataset.tone = count === 0 ? 'closed' : count <= 10 ? 'limited' : 'available';
  const fromPrice = byId('event-from-price');
  fromPrice.hidden = available.length === 0;
  fromPrice.textContent = available.length ? 'Desde ' + amount(Math.min(...available.map(value => value.precio))) : '';
  byId('mobile-from-price').textContent = fromPrice.textContent;
  byId('event-mobile-bar').hidden = available.length === 0;
  byId('booking-controls').hidden = count === 0;
  byId('event-unavailable').hidden = count > 0;
  byId('event-unavailable-title').textContent = status.textContent;
  byId('event-unavailable-message').textContent = status.textContent === 'Finalizado' ? 'Este evento ya terminó.'
    : status.textContent === 'Agotado' ? 'Por el momento, no quedan entradas disponibles.' : 'Este evento no tiene entradas a la venta en este momento.';
  if (!count && sectorUpdates.size) {
    byId('event-unavailable-message').textContent += ' ' + [...sectorUpdates.values()].at(-1)?.text;
  }
  byId('availability-status').textContent = count === 1 ? 'Queda 1 entrada' : count + ' entradas disponibles';
  const list = byId('sector-list'); list.replaceChildren();
  context.sectors.forEach(current => {
    const button = document.createElement('button'); button.type = 'button'; button.className = 'sector-option'; button.dataset.sector = String(current.idSector);
    const name = document.createElement('span'); name.className = 'sector-name';
    const title = document.createElement('strong'); title.dataset.sectorName = String(current.idSector); title.textContent = sectorName(current.idSector);
    const stock = document.createElement('small');
    stock.textContent = sectorType(current) + ' · ' + (current.cancelado ? 'Cancelado' : current.disponibles === 0 ? 'Agotado' : current.disponibles + ' disponibles');
    name.append(title, stock);
    if (sectorUpdates.has(current.idSector)) {
      const change = document.createElement('small'); change.className = 'sector-change-label'; change.textContent = 'Disponibilidad o precio actualizado';
      name.append(change);
    }
    const price = document.createElement('span'); price.className = 'sector-price'; price.textContent = amount(current.precio) + ' por entrada';
    button.disabled = busy || !context?.open || !sellable(current); button.append(name, price); list.append(button);
  });
  paintSector();
}

async function loadSectorNames() {
  const pending = context?.sectors.filter(sector => !sectorNames.has(sector.idSector)) ?? [];
  for (let offset = 0; offset < pending.length && isCurrentOwner(); offset += 4) {
    await Promise.all(pending.slice(offset, offset + 4).map(async ({ idSector }) => {
      try {
        const { data } = await api<{ id: number; nombre: string }>('/sectores/' + idSector);
        if (!isCurrentOwner() || data?.id !== idSector || typeof data.nombre !== 'string' || !data.nombre.trim()) return;
        sectorNames.set(idSector, data.nombre.trim());
        document.querySelectorAll('[data-sector-name="' + idSector + '"]').forEach(label => label.textContent = sectorName(idSector));
        if (selectedSector === idSector) {
          byId('sector-title').textContent = sectorName(idSector);
          byId('seat-list').querySelectorAll<HTMLButtonElement>('[data-seat]').forEach(button => {
            button.title = button.title.replace(' · Sector #' + idSector + ' · ', ' · ' + sectorName(idSector) + ' · ');
            button.setAttribute('aria-label', button.title);
          });
          summary();
        }
      } catch { /* La identificación del sector sigue disponible si falla su nombre. */ }
    }));
  }
}

function reconcileSelection(previous: Draft, before: Availability | undefined, fresh: Availability | undefined, open: boolean) {
  const next = open && fresh ? reconcileDraft(previous, fresh).draft : null;
  const removed = previous.seatIds.filter(id => !next?.seatIds.includes(id));
  const removedCount = previous.quantity - (next?.quantity ?? 0);
  const priceChanged = before !== undefined && fresh !== undefined && before.precio !== fresh.precio;
  const notes: string[] = [];
  if (removed.length) {
    const seats = (before ?? fresh)?.asientos.filter(seat => removed.includes(seat.idAsiento)) ?? [];
    notes.push(seats.length ? 'Asientos retirados: ' + seats.map(seat => 'fila ' + seat.fila + ', asiento ' + seat.columna).join(' · ') + '.'
      : 'Se retiraron ' + removed.length + ' asientos por el cambio de cupo.');
  } else if (removedCount) notes.push('El cupo cambió. Se quitaron ' + removedCount + (removedCount === 1 ? ' entrada.' : ' entradas.'));
  if (priceChanged) notes.push('El precio por entrada cambió de ' + amount(before.precio) + ' a ' + amount(fresh.precio) + '.');
  if (notes.length) sectorUpdates.set(previous.sectorId, { text: notes.join(' '), seats: new Set(removed) });
  draft = next;
  return notes.length > 0;
}

async function load() {
  if (busy || !isCurrentOwner()) return; busy = true; review.disabled = true; save.disabled = true;
  byId('event-state').hidden = false; byId('event-state').setAttribute('aria-busy', 'true'); byId('event-content').hidden = true;
  byId('event-state').dataset.mode = 'loading';
  byId('retry-event').hidden = true; byId('back-event-error').hidden = true;
  byId('event-error').hidden = true; byId('event-state-title').textContent = 'Cargando evento y disponibilidad…';
  const validLink = positiveId(eventId) && (requestedSector === undefined || positiveId(requestedSector));
  try {
    if (!validLink) throw new Error('El enlace no contiene un evento válido.');
    const previous = draft;
    const before = sector();
    const loaded = await selectionContext(eventId);
    if (!isCurrentOwner()) return;
    context = loaded;
    const preferred = selectedSector ?? requestedSector;
    selectedSector = context.sectors.some(value => value.idSector === preferred) ? preferred : context.sectors.find(sellable)?.idSector;
    let saved = previous;
    if (!saved && !restored && params.get('carrito') === '1') {
      try { saved = getCart().find(item => item.eventId === eventId && item.sectorId === selectedSector) || null; }
      catch (cause) { message.textContent = cause instanceof Error ? cause.message : 'No pudimos leer el carrito.'; }
    }
    restored = true;
    if (saved) {
      const changed = reconcileSelection(saved, before, sector(), context.open);
      message.textContent = changed ? 'Revisá la selección actualizada.' : previous ? 'Disponibilidad actualizada.' : 'Selección recuperada y verificada.';
      if (changed && saved.sectorId !== selectedSector) message.textContent += ' ' + sectorUpdates.get(saved.sectorId)?.text;
    } else draft = null;
    busy = false; paint(); void loadSectorNames();
  } catch (cause) {
    if (!isCurrentOwner()) return;
    const missing = !validLink || (cause instanceof ApiError && cause.status === 404);
    context = null; draft = null;
    byId('event-state-title').textContent = missing ? 'Evento no encontrado' : 'No pudimos consultar el evento';
    byId('event-state').dataset.mode = missing ? 'missing' : 'error';
    const error = byId('event-error');
    error.textContent = missing ? 'El enlace no corresponde a un evento publicado en el catálogo.' : cause instanceof Error && cause.name !== 'ApiConnectionError'
      ? cause.message : 'Revisá tu conexión y volvé a intentar.';
    error.hidden = false;
    byId('retry-event').hidden = missing;
    byId('back-event-error').hidden = false;
  } finally { busy = false; byId('event-state').setAttribute('aria-busy', 'false'); }
}
byId('retry-event').addEventListener('click', load);
byId('refresh-availability').addEventListener('click', () => { dialog.close(); load(); });
byId('sector-list').addEventListener('click', event => {
  const button = (event.target as Element).closest<HTMLButtonElement>('[data-sector]'); if (!button || button.disabled || busy) return;
  if (selectedSector !== Number(button.dataset.sector)) { selectedSector = Number(button.dataset.sector); draft = null; message.textContent = 'Nuevo sector: se limpió la selección sin guardar. Cada selección corresponde a una compra por sector.'; }
  paintSector();
});
quantity.addEventListener('input', () => {
  const current = sector(); if (!current || busy || quantity.disabled) return;
  const count = Number(quantity.value);
  const maximum = Math.min(current.disponibles, 1000);
  if (!Number.isInteger(count) || count < 0 || count > maximum) {
    quantity.setAttribute('aria-invalid', 'true');
    draft = null; message.textContent = 'Ingresá un número entero entre 0 y ' + maximum + '.'; summary(); return;
  }
  quantity.removeAttribute('aria-invalid');
  draft = newDraft(current, count); message.textContent = ''; summary();
});
for (const [button, change] of [[subtractQuantity, -1], [addQuantity, 1]] as const) {
  button.addEventListener('click', () => {
    quantity.value = String((draft?.quantity ?? 0) + change);
    quantity.dispatchEvent(new Event('input'));
  });
}
byId('seat-list').addEventListener('click', event => {
  const button = (event.target as Element).closest<HTMLButtonElement>('[data-seat]'), current = sector();
  if (!button || button.disabled || !current || busy) return;
  const id = Number(button.dataset.seat), ids = draft?.seatIds || [];
  const values = ids.includes(id) ? ids.filter(value => value !== id) : [...ids, id];
  if (values.length > Math.min(current.disponibles, 1000)) { message.textContent = 'La selección supera el cupo consultado.'; return; }
  draft = newDraft(current, values.length, values);
  message.textContent = '';
  button.setAttribute('aria-pressed', String(values.includes(id)));
  button.dataset.seatTone = values.includes(id) ? 'selected' : 'available';
  button.title = button.title.replace(/ · (Seleccionado|Disponible)$/, values.includes(id) ? ' · Seleccionado' : ' · Disponible');
  button.setAttribute('aria-label', button.title);
  summary();
});
byId('clear-selection').addEventListener('click', () => { draft = null; message.textContent = 'Selección vacía.'; paintSector(); });
byId('jump-to-sectors').addEventListener('click', () => byId('seleccion').focus({ preventScroll: true }));
review.addEventListener('click', () => { if (!review.disabled) { byId('save-cart-status').textContent = ''; byId('view-cart').hidden = true; dialog.showModal(); purchaseUI?.refresh(); } });
byId('mobile-review-selection').addEventListener('click', () => review.click());
byId('close-review').addEventListener('click', () => { dialog.close(); purchaseUI?.refresh(); });
save.addEventListener('click', async () => {
  if (!draft || busy || !isCurrentOwner()) return;
  const selection = draft, before = sector();
  busy = true; save.disabled = true; save.setAttribute('aria-busy', 'true'); review.disabled = true; byId('save-cart-status').textContent = 'Revalidando disponibilidad antes de guardar…';
  purchaseUI?.refresh();
  try {
    const fresh = await selectionContext(eventId, selection.sectorId), current = fresh.sectors[0];
    if (!isCurrentOwner()) return;
    context = { ...fresh, sectors: context!.sectors.map(value => value.idSector === current.idSector ? current : value) };
    if (reconcileSelection(selection, before, current, fresh.open)) {
      dialog.close(); message.textContent = 'Revisá la selección actualizada antes de guardarla.';
    } else {
      saveDraft({ ...selection, updatedAt: new Date().toISOString() }, owner);
      byId('save-cart-status').textContent = 'Selección guardada solo en este navegador. No se reservaron lugares ni se creó una compra.';
    }
  } catch (cause) { if (isCurrentOwner()) byId('save-cart-status').textContent = cause instanceof Error ? cause.message : 'No pudimos verificar ni guardar la selección.'; }
  finally {
    busy = false;
    save.removeAttribute('aria-busy');
    if (isCurrentOwner()) {
      paint();
      if (dialog.open) byId('view-cart').hidden = !byId('save-cart-status').textContent?.startsWith('Selección guardada');
    }
  }
});
function hideInactiveEvent() {
  if (isCurrentOwner()) return;
  byId('event-content').hidden = true;
  if (dialog.open) dialog.close();
}
window.addEventListener('ticketya:profile', hideInactiveEvent);
window.addEventListener('pageshow', event => {
  if (getAuthenticatedUserId() !== owner) { hideInactiveEvent(); location.reload(); }
  else if (event.persisted) { dialog.close(); load(); }
});
purchaseUI = initPurchase(owner, eventId,
  () => !busy && context?.open && !review.disabled ? draft : null,
  () => { dialog.close(); load(); },
  () => context?.event.high === true
);
if (owner === null) location.replace('/login/?volver=' + encodeURIComponent(location.pathname + location.search + location.hash));
else load();
