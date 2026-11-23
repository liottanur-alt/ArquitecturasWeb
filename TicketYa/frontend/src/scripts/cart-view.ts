import { getCart, removeDraft } from './cart';
import { getSelectedProfileId } from './session';
import { selectionContext } from './selection-api';
import { eventLink, reconcileDraft } from './selection-data';
export function initCart() {
  let version = 0;
  const list = document.querySelector<HTMLElement>('#cart-list')!;
  const status = document.querySelector<HTMLElement>('#cart-status')!;
  const text = (tag: string, value: string, className = '') => { const el = document.createElement(tag); el.textContent = value; el.className = className; return el; };
  async function render() {
    const currentVersion = ++version, owner = getSelectedProfileId();
    list.replaceChildren(); document.querySelector<HTMLElement>('#cart-total-box')!.hidden = true;
    status.textContent = 'Consultando disponibilidad actual del carrito…';
    try {
      const items = getCart();
      document.querySelector('#cart-count')!.textContent = String(items.length);
      document.querySelector<HTMLElement>('#cart-empty')!.hidden = items.length > 0;
      await Promise.all(items.map(async draft => {
        const card = document.createElement('article'); card.className = 'purchase-card cart-card';
        const img = document.createElement('img'); img.src = '/event-placeholder.svg'; img.alt = 'Imagen ilustrativa de respaldo';
        const content = document.createElement('div'); content.className = 'purchase-info';
        const title = text('h3', 'Evento #' + draft.eventId);
        const details = text('p', 'Sector #' + draft.sectorId + ' · ' + draft.quantity + ' entradas · ' + draft.type + (draft.seatIds.length ? ' · IDs: ' + draft.seatIds.join(', ') : ''));
        const validation = text('p', 'Verificando evento y disponibilidad…'); validation.setAttribute('role', 'status');
        const price = text('strong', 'Importe sin verificar');
        const link = document.createElement('a'); link.href = eventLink(draft.eventId, draft.sectorId); link.textContent = 'Revisar selección ↗';
        const footer = document.createElement('div'); footer.className = 'purchase-bottom'; footer.append(price, link);
        const remove = document.createElement('button'); remove.className = 'remove-draft'; remove.textContent = 'Quitar del carrito';
        remove.addEventListener('click', () => {
          if (getSelectedProfileId() !== owner) { status.textContent = 'El perfil cambió. Volvé a cargar el carrito.'; return; }
          try { removeDraft(draft.eventId, draft.sectorId); render(); }
          catch (cause) { status.textContent = cause instanceof Error ? cause.message : 'No pudimos actualizar el carrito.'; }
        });
        content.append(text('span', 'SELECCIÓN LOCAL · NO RESERVADA', 'cart-badge'), title, details, validation, footer, remove); card.append(img, content); list.append(card);
        try {
          const fresh = await selectionContext(draft.eventId, draft.sectorId);
          if (currentVersion !== version || getSelectedProfileId() !== owner) return;
          title.textContent = fresh.event.title;
          const current = fresh.sectors[0];
          const result = fresh.open ? reconcileDraft(draft, current) : { draft: null, changed: true };
          if (result.changed) {
            validation.textContent = 'La selección guardada cambió o ya no está disponible. Revisá asientos y cantidades antes de continuar.';
            price.textContent = 'Importe por revisar';
          } else {
            validation.textContent = 'Coincide con la disponibilidad consultada. Esto no reserva ni garantiza lugares.';
            price.textContent = (current.precio * draft.quantity).toLocaleString('es-AR', { maximumFractionDigits: 2 }) + ' (importe consultado)';
          }
        } catch (cause) {
          if (currentVersion !== version || getSelectedProfileId() !== owner) return;
          validation.textContent = 'Sin verificar: ' + (cause instanceof Error ? cause.message : 'No se pudo consultar disponibilidad.');
          price.textContent = 'Importe sin verificar';
        }
      }));
      if (currentVersion === version && getSelectedProfileId() === owner) status.textContent = 'Carrito local separado por perfil. Cada selección corresponde a un sector. Guardar no reserva lugares; el servidor valida límites y disponibilidad al comprar.';
    } catch (cause) {
      status.textContent = cause instanceof Error ? cause.message : 'No pudimos leer el carrito local.';
      document.querySelector('#cart-count')!.textContent = '—'; document.querySelector<HTMLElement>('#cart-empty')!.hidden = true;
    }
  }
  document.querySelector('#refresh-cart')!.addEventListener('click', render);
  window.addEventListener('pageshow', event => { if (event.persisted) render(); });
  window.addEventListener('storage', render);
  render();
  return render;
}
