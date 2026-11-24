import { api, ApiError } from './api';
import { mapCatalog, setEventImage, type CatalogEvent } from './catalog-data';
import { catalogWindow, latestRequest, pageSize, searchPath } from './catalog-search';
import { eventLink, parseAvailability, saleOpen } from './selection-data';
import { parseRecommendations } from './recommendations-data';
import { getAuthenticatedUserId } from './session';

const el = <T extends HTMLElement = HTMLElement>(id: string) => document.getElementById(id) as T;
const form = el<HTMLFormElement>('home-search-form');
const query = el<HTMLInputElement>('query'), city = el<HTMLInputElement>('city');
const category = el<HTMLSelectElement>('home-category'), date = el<HTMLInputElement>('home-date');
const grid = el('event-grid'), featured = el('featured-grid');
const previous = el<HTMLButtonElement>('catalog-prev'), next = el<HTMLButtonElement>('catalog-next');
const state = el('catalog-state'), retry = el<HTMLButtonElement>('retry-catalog');
const owner = getAuthenticatedUserId();
const active = () => owner !== null && getAuthenticatedUserId() === owner;
const runLatest = latestRequest();
const runRecommendations = latestRequest();
const recommendations = el('home-recommendation-grid');
const recommendationSection = el('para-vos');
const recommendationStatus = el('home-recommendation-status');
const retryRecommendations = el<HTMLButtonElement>('retry-home-recommendations');
const cities = new Set<string>();
const money = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS', maximumFractionDigits: 2, minimumFractionDigits: 0 });
date.min = catalogWindow('').from.slice(0, 10);
const reduced = matchMedia('(prefers-reduced-motion: reduce)');
const observer = typeof IntersectionObserver === 'undefined' ? null : new IntersectionObserver(entries => {
  for (const entry of entries) if (entry.isIntersecting) {
    entry.target.classList.add('home-revealed');
    observer?.unobserve(entry.target);
  }
}, { threshold: .12 });

const favoritesKey = 'ticketya-event-favorites-' + owner;
let favorites: number[] = [];
try {
  const value: unknown = JSON.parse(localStorage.getItem(favoritesKey) ?? '[]');
  if (Array.isArray(value)) favorites = value.filter(Number.isSafeInteger);
} catch { /* El almacenamiento puede estar desactivado. */ }

type Stock = { count: number; price: number | null };
let dateMode = '', limited = false, page = 0, attemptedPage = 0, attemptedDirection = 1, requestId = 0;
let debounce: ReturnType<typeof setTimeout>, toastTimer: ReturnType<typeof setTimeout>;

function reveal(element: HTMLElement, index: number) {
  if (!observer || reduced.matches) return;
  element.style.setProperty('--reveal-delay', Math.min(index, 5) * 55 + 'ms');
  element.classList.add('home-reveal');
  observer.observe(element);
}

function skeletons() {
  return Array.from({ length: 4 }, () => {
    const card = document.createElement('div');
    card.className = 'home-card home-card-skeleton';
    card.setAttribute('aria-hidden', 'true');
    return card;
  });
}

function updateOptions(events: CatalogEvent[]) {
  events.forEach(event => { if (event.city) cities.add(event.city); });
  el('catalog-cities').replaceChildren(...[...cities].sort((a, b) => a.localeCompare(b, 'es-AR')).map(value => new Option(value, value)));
}

function syncFilters() {
  document.querySelectorAll<HTMLButtonElement>('[data-category-filter], [data-date-filter], #limited-filter').forEach(button => {
    const pressed = button.dataset.categoryFilter ? button.dataset.categoryFilter === category.value
      : button.dataset.dateFilter ? button.dataset.dateFilter === dateMode : limited;
    button.setAttribute('aria-pressed', String(pressed));
  });
  el('clear-filters').hidden = !(query.value || city.value || category.value || date.value || dateMode || limited);
}

function fill(template: string, event: CatalogEvent) {
  const fragment = document.querySelector<HTMLTemplateElement>('#' + template)!.content.cloneNode(true) as DocumentFragment;
  const bindings = { title: event.title, category: event.category, location: event.location, date: event.ticketDate,
    action: event.state === 'En curso' ? 'Comprar entradas' : 'Ver detalles' };
  for (const [key, value] of Object.entries(bindings)) fragment.querySelectorAll('[data-' + key + ']').forEach(node => node.textContent = value);
  fragment.querySelectorAll<HTMLAnchorElement>('[data-event]').forEach(link => link.href = eventLink(event.id));
  const image = fragment.querySelector('img')!;
  setEventImage(image, event.image);
  return fragment;
}

async function getStock(event: CatalogEvent, signal: AbortSignal): Promise<Stock | null> {
  if (event.state !== 'En curso') return null;
  try {
    const { data } = await api('/eventos/' + event.id + '/sectores', { cache: 'no-store', signal });
    if (!Array.isArray(data) || !data.length) return null;
    const sectors = data.map(value => parseAvailability(value, event.id));
    if (new Set(sectors.map(sector => sector.idSector)).size !== sectors.length) return null;
    const sale = sectors.filter(sector => !sector.cancelado && sector.disponibles > 0);
    return { count: sale.reduce((sum, sector) => sum + sector.disponibles, 0), price: sale.length ? Math.min(...sale.map(sector => sector.precio)) : null };
  } catch { return null; }
}

async function stockFor(events: CatalogEvent[], signal: AbortSignal, paint?: (id: number, stock: Stock | null) => void) {
  const stocks = new Map<number, Stock | null>();
  for (let i = 0; i < events.length && !signal.aborted; i += 4) {
    await Promise.all(events.slice(i, i + 4).map(async event => {
      const stock = await getStock(event, signal);
      stocks.set(event.id, stock);
      if (!signal.aborted) paint?.(event.id, stock);
    }));
  }
  return stocks;
}

function paintStock(card: ParentNode, event: CatalogEvent, stock: Stock | null | undefined) {
  const badge = card.querySelector<HTMLElement>('[data-state]')!;
  const status = event.state === 'En curso'
    ? stock?.count === 0 ? 'Agotado' : stock && stock.count <= 10 ? 'Últimas entradas' : ''
    : event.state === 'Estado no informado' ? '' : event.state;
  badge.hidden = !status;
  badge.textContent = status;
  badge.dataset.tone = status === 'Últimas entradas' ? 'limited' : 'closed';
  card.querySelector('[data-price]')!.textContent = stock?.price != null ? 'Desde ' + money.format(stock.price)
    : stock === undefined && event.state === 'En curso' ? 'Consultando precio…' : event.state === 'En curso' && !status ? 'Ver precio' : '';
  const action = card.querySelector('[data-action]')!;
  const canBuy = event.state === 'En curso' && status !== 'Agotado';
  action.textContent = canBuy ? 'Comprar entradas' : 'Ver detalles';
  action.closest('a')?.setAttribute('aria-label', action.textContent + ' de ' + event.title);
}

function paintCards(events: CatalogEvent[], stocks: Map<number, Stock | null>, container = grid, reasons = new Map<number, string>()) {
  const cards = new Map<number, HTMLElement>();
  container.replaceChildren();
  events.forEach((event, index) => {
    const fragment = fill('event-card-template', event);
    const card = fragment.querySelector<HTMLElement>('article')!;
    const favorite = fragment.querySelector<HTMLButtonElement>('[data-favorite]')!;
    const reason = fragment.querySelector<HTMLElement>('[data-reason]')!;
    reason.textContent = reasons.get(event.id) ?? '';
    reason.hidden = !reason.textContent;
    favorite.dataset.favorite = String(event.id);
    favorite.setAttribute('aria-pressed', String(favorites.includes(event.id)));
    favorite.setAttribute('aria-label', (favorites.includes(event.id) ? 'Quitar de favoritos: ' : 'Guardar en favoritos: ') + event.title);
    paintStock(card, event, stocks.get(event.id));
    cards.set(event.id, card);
    container.append(fragment);
    reveal(card, index);
  });
  return cards;
}

async function loadRecommendations() {
  if (!active()) return;
  recommendationSection.hidden = false;
  recommendationStatus.textContent = 'Buscando recomendaciones para vos…';
  recommendations.setAttribute('aria-busy', 'true');
  retryRecommendations.hidden = true;
  try {
    const response = await runRecommendations(async signal => {
      const { data } = await api('/usuarios/' + owner + '/recomendaciones', { signal, cache: 'no-store' });
      const result = parseRecommendations(data);
      const picks = result.recomendaciones.slice(0, 4);
      const details = await Promise.allSettled(picks.map(async pick => {
        let detail: unknown;
        try {
          detail = (await api('/api/eventos/' + pick.idEvento, { signal })).data;
        } catch (error) {
          if (error instanceof ApiError && error.status === 404) return null;
          if (signal.aborted) throw error;
          const [event] = mapCatalog([{
            id: pick.idEvento, nombre: pick.nombre, tipo: pick.tipo, fechaHora: pick.fechaHora,
          }]);
          return { event: { ...event, location: pick.ciudad || 'Ubicación no informada' }, complete: false };
        }
        if (!saleOpen(detail)) return null;
        const event = mapCatalog([detail])[0];
        if (event.id !== pick.idEvento) throw new Error('El detalle no coincide con la recomendación.');
        return { event, complete: true };
      }));
      return { result, picks, details, signal };
    });
    if (!active() || !response.current) return;
    const { result, picks, details, signal } = response.data;
    const resolved = details.flatMap(detail => detail.status === 'fulfilled' && detail.value ? [detail.value] : []);
    const events = resolved.map(({ event }) => event);
    const hadErrors = details.some(detail => detail.status === 'rejected');
    const incomplete = hadErrors || resolved.some(event => !event.complete);
    const reasons = new Map(picks.map(pick => [pick.idEvento, pick.motivos[0] ?? '']));
    const cards = paintCards(events, new Map(), recommendations, reasons);
    recommendationStatus.textContent = events.length
      ? result.parcial || incomplete ? 'Algunas sugerencias tienen información limitada. Consultá el evento para confirmar el cupo.' : ''
      : result.parcial || hadErrors ? 'No pudimos completar tus recomendaciones en este momento.'
        : 'Todavía no encontramos eventos que coincidan. Podés agregar categorías, una ciudad o artistas favoritos en tus preferencias.';
    retryRecommendations.hidden = !incomplete && !result.parcial;
    recommendations.removeAttribute('aria-busy');
    void stockFor(events, signal, (id, stock) => {
      const card = cards.get(id), event = events.find(item => item.id === id);
      if (card?.isConnected && event && active()) paintStock(card, event, stock);
    });
  } catch {
    if (!active()) return;
    recommendations.replaceChildren();
    recommendations.removeAttribute('aria-busy');
    recommendationStatus.textContent = 'No pudimos cargar tus recomendaciones.';
    retryRecommendations.hidden = false;
  }
}

async function loadFeatured() {
  const feedback = el('featured-feedback');
  try {
    const { data } = await api(searchPath('', '', 0, '', catalogWindow('')));
    if (!active()) return;
    const events = mapCatalog(data);
    updateOptions(events);
    const picks = events.filter(event => event.state === 'En curso').slice(0, 3);
    featured.replaceChildren(...picks.map(event => fill('featured-template', event)));
    featured.dataset.count = String(picks.length);
    if (picks.length === 0) { feedback.hidden = false; feedback.textContent = 'Encontrá tu próximo plan en el catálogo.'; }
  } catch {
    if (!active()) return;
    featured.replaceChildren();
    feedback.hidden = false;
    feedback.textContent = 'Los destacados no están disponibles en este momento.';
  } finally { featured.setAttribute('aria-busy', 'false'); }
}

async function loadCatalog(target = 0, direction = 1) {
  if (!active()) return;
  attemptedPage = target; attemptedDirection = direction;
  const version = ++requestId;
  const filters = { category: category.value, city: city.value, query: query.value, date: catalogWindow(dateMode || date.value), limited };
  syncFilters();
  previous.disabled = next.disabled = true;
  state.hidden = true;
  grid.setAttribute('aria-busy', 'true');
  grid.replaceChildren(...skeletons());
  el('catalog-status').textContent = 'Buscando eventos…';
  el('catalog-page').textContent = '';
  try {
    const response = await runLatest(async signal => {
      let position = target;
      for (;;) {
        const { data } = await api(searchPath(filters.category, filters.city, position, filters.query, filters.date), { signal });
        const events = mapCatalog(data);
        const stocks = filters.limited ? await stockFor(events, signal) : new Map<number, Stock | null>();
        const visible = filters.limited ? events.filter(event => {
          const count = stocks.get(event.id)?.count ?? 0;
          return count > 0 && count <= 10;
        }) : events;
        const hasNext = events.length === pageSize;
        if (visible.length || !filters.limited || signal.aborted || (direction > 0 ? !hasNext : position === 0)) {
          return { events, visible, stocks, position, signal, hasNext };
        }
        position += direction;
      }
    });
    if (!response.current || !active()) return;
    const { events, visible, stocks, position, signal, hasNext } = response.data;
    page = position;
    updateOptions(events);
    const cards = paintCards(visible, stocks);
    grid.setAttribute('aria-busy', 'false');
    el('catalog-status').textContent = visible.length ? visible.length + (visible.length === 1 ? ' evento' : ' eventos') : '';
    el('catalog-page').textContent = 'Página ' + (page + 1);
    previous.disabled = page === 0; next.disabled = !hasNext;
    if (!visible.length) {
      state.hidden = false; retry.hidden = true;
      el('catalog-state-title').textContent = 'No encontramos eventos';
      el('catalog-error').textContent = 'Probá con otra fecha, ciudad o categoría.';
    }
    if (!filters.limited) void stockFor(visible, signal, (id, stock) => {
      const event = visible.find(item => item.id === id), card = cards.get(id);
      if (active() && version === requestId && event && card) paintStock(card, event, stock);
    });
  } catch (error) {
    if (!active() || version !== requestId) return;
    grid.replaceChildren();
    grid.setAttribute('aria-busy', 'false');
    state.hidden = false; retry.hidden = false;
    el('catalog-status').textContent = '';
    el('catalog-state-title').textContent = 'No pudimos cargar los eventos';
    el('catalog-error').textContent = error instanceof Error && error.name !== 'ApiConnectionError'
      ? error.message : 'Revisá la conexión y volvé a intentar.';
  }
}

const jump = () => el('catalogo').scrollIntoView({ block: 'start', behavior: reduced.matches ? 'instant' : 'smooth' });
const refresh = () => { clearTimeout(debounce); void loadCatalog(); };
form.addEventListener('submit', event => { event.preventDefault(); refresh(); jump(); });
for (const input of [query, city]) input.addEventListener('input', () => {
  syncFilters(); clearTimeout(debounce); debounce = setTimeout(refresh, 320);
});
category.addEventListener('change', refresh);
date.addEventListener('change', () => { dateMode = ''; refresh(); });
document.querySelectorAll<HTMLButtonElement>('[data-category-filter], [data-date-filter]').forEach(button => {
  button.addEventListener('click', () => {
    const type = button.dataset.categoryFilter;
    if (type) category.value = category.value === type ? '' : type;
    else { dateMode = dateMode === button.dataset.dateFilter ? '' : button.dataset.dateFilter ?? ''; date.value = ''; }
    refresh();
    if (button.hasAttribute('data-jump')) jump();
  });
});
el('limited-filter').addEventListener('click', () => { limited = !limited; refresh(); });
el('clear-filters').addEventListener('click', () => { form.reset(); dateMode = ''; limited = false; refresh(); });
retry.addEventListener('click', () => void loadCatalog(attemptedPage, attemptedDirection));
previous.addEventListener('click', () => { if (page > 0) { void loadCatalog(page - 1, -1); jump(); } });
next.addEventListener('click', () => { void loadCatalog(page + 1); jump(); });

for (const container of [grid, recommendations]) container.addEventListener('click', event => {
  const button = (event.target as Element).closest<HTMLButtonElement>('[data-favorite]');
  if (!button || !active()) return;
  const id = Number(button.dataset.favorite);
  const exists = favorites.includes(id);
  const values = exists ? favorites.filter(value => value !== id) : [...favorites, id];
  const toast = el('toast');
  try {
    localStorage.setItem(favoritesKey, JSON.stringify(values));
    favorites = values;
    document.querySelectorAll<HTMLButtonElement>('[data-favorite="' + id + '"]').forEach(control => {
      control.setAttribute('aria-pressed', String(!exists));
      control.setAttribute('aria-label', (exists ? 'Guardar en favoritos: ' : 'Quitar de favoritos: ') + control.closest('article')?.querySelector('h3')?.textContent);
    });
    toast.textContent = exists ? 'Evento quitado de tus favoritos' : 'Evento guardado en este dispositivo';
  } catch { toast.textContent = 'No pudimos guardar el favorito en este dispositivo.'; }
  toast.classList.add('visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('visible'), 2500);
});

document.querySelectorAll<HTMLElement>('[data-jump]').forEach(reveal);
window.addEventListener('ticketya:profile', () => {
  if (!active()) { grid.replaceChildren(); featured.replaceChildren(); recommendations.replaceChildren(); recommendationSection.hidden = true; }
});
retryRecommendations.addEventListener('click', () => void loadRecommendations());
window.addEventListener('ticketya:recommendations-refresh', () => void loadRecommendations());
window.addEventListener('pageshow', event => {
  if (getAuthenticatedUserId() !== owner) location.reload();
  else if (event.persisted) void loadRecommendations();
});
if (owner === null) location.replace('/login/?volver=' + encodeURIComponent(location.pathname + location.search + location.hash));
else { void loadFeatured(); void loadCatalog(); void loadRecommendations(); }
