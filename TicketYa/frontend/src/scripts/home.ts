import { eventLink } from './selection-data';
import gsap from 'gsap';
import { api } from './api';
import { mapCatalog, type CatalogEvent } from './catalog-data';
import { latestRequest, matchesText, pageSize, searchPath } from './catalog-search';

const query = document.querySelector<HTMLInputElement>('#query')!;
const city = document.querySelector<HTMLInputElement>('#city')!;
const state = document.querySelector<HTMLElement>('#catalog-state')!;
const status = document.querySelector<HTMLElement>('#catalog-status')!;
const error = document.querySelector<HTMLElement>('#catalog-error')!;
const retry = document.querySelector<HTMLButtonElement>('#retry-catalog')!;
const previous = document.querySelector<HTMLButtonElement>('#catalog-prev')!;
const next = document.querySelector<HTMLButtonElement>('#catalog-next')!;
const pageLabel = document.querySelector<HTMLElement>('#catalog-page')!;
const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
const runLatest = latestRequest();
let events: CatalogEvent[] = [], tipo = '', page = 0, loading = false;
const cities = new Set<string>();
let currentSlide = 0, paused = false, hovering = false, focusing = false;
let timer: ReturnType<typeof setInterval>;
let favorites: number[] = [];
try { const saved: unknown = JSON.parse(localStorage.getItem('ticketya-favorites') || '[]'); if (Array.isArray(saved)) favorites = saved.filter(Number.isSafeInteger); } catch {}

function fill(container: ParentNode, event: CatalogEvent) {
  const bindings: Record<string, string> = { title: event.title, 'category-label': event.category, artist: event.artist, location: event.location,
    date: event.fullDate, day: event.day, month: event.month, 'month-year': event.monthYear, description: event.description };
  for (const [key, value] of Object.entries(bindings)) container.querySelectorAll('[data-' + key + ']').forEach(el => el.textContent = value);
  container.querySelectorAll<HTMLElement>('[data-event]').forEach(el => { el.dataset.event = String(event.id); el.setAttribute('aria-label', 'Ver ' + event.title); });
}
function renderCatalog() {
  const visible = events.filter(event => matchesText(event, query.value));
  const viewport = document.querySelector('.hero-viewport')!;
  const dots = document.querySelector('.hero-dots')!;
  viewport.replaceChildren(); dots.replaceChildren();
  visible.slice(0, 3).forEach((event, i) => {
    const slide = (document.querySelector<HTMLTemplateElement>('#event-slide-template')!.content.cloneNode(true) as DocumentFragment);
    fill(slide, event);
    const article = slide.querySelector<HTMLElement>('article')!;
    article.classList.toggle('active', i === 0); article.inert = i !== 0; article.setAttribute('aria-hidden', String(i !== 0));
    viewport.append(slide);
    const dot = document.createElement('button'); dot.className = 'hero-dot' + (i === 0 ? ' selected' : ''); dot.dataset.slideTo = String(i);
    dot.setAttribute('aria-label', 'Mostrar ' + event.title); dot.setAttribute('aria-current', String(i === 0)); dot.append(document.createElement('span')); dots.append(dot);
  });
  document.querySelector('#slide-total')!.textContent = String(Math.min(visible.length, 3)).padStart(2, '0');
  document.querySelectorAll<HTMLElement>('[data-events-section]').forEach(section => {
    const rail = section.querySelector('.event-rail')!;
    rail.replaceChildren();
    const items = section.id === 'alta-demanda' ? visible.filter(event => event.high) : visible;
    items.forEach(event => {
      const card = document.querySelector<HTMLTemplateElement>('#event-card-template')!.content.cloneNode(true) as DocumentFragment;
      fill(card, event);
      const article = card.querySelector<HTMLElement>('article')!;
      article.dataset.title = [event.title, event.artist, event.venue].join(' '); article.dataset.city = event.city; article.dataset.type = event.category;
      card.querySelector<HTMLElement>('[data-high]')!.hidden = !event.high;
      card.querySelector<HTMLButtonElement>('[data-favorite]')!.dataset.favorite = String(event.id);
      rail.append(card);
    });
    section.hidden = events.length === 0;
    section.querySelector<HTMLElement>('.empty-state')!.hidden = items.length > 0;
    section.querySelector('.empty-state p')!.textContent = section.id === 'alta-demanda'
      ? 'No hay eventos de alta demanda que coincidan en esta página.' : 'No hay coincidencias de texto en esta página. Probá otra búsqueda o avanzá de página.';
  });
  document.querySelector<HTMLElement>('.hero')!.hidden = visible.length === 0;
  document.querySelector<HTMLElement>('.hero-controls')!.hidden = visible.length < 2;
  currentSlide = 0; updateCarousel(); updateRails(); updateFavorites();
  document.querySelector('#filter-status')!.textContent = visible.length + ' coincidencias de texto en los ' + events.length + ' resultados cargados de esta página.';
  document.querySelector<HTMLElement>('#clear-filters')!.hidden = !query.value && !city.value && !tipo;

}

function updateFavorites() {
  document.querySelectorAll<HTMLButtonElement>('[data-favorite]').forEach(button => {
    const selected = favorites.includes(Number(button.dataset.favorite));
    button.setAttribute('aria-pressed', String(selected));
    button.setAttribute('aria-label', (selected ? 'Quitar' : 'Guardar') + ' evento en favoritos de este dispositivo');
  });
}
function updateRails() {
  document.querySelectorAll<HTMLElement>('[data-events-section]').forEach(section => {
    const rail = section.querySelector<HTMLElement>('.event-rail')!;
    section.querySelector<HTMLButtonElement>('.rail-prev')!.disabled = rail.scrollLeft <= 2;
    section.querySelector<HTMLButtonElement>('.rail-next')!.disabled = rail.scrollLeft + rail.clientWidth >= rail.scrollWidth - 3;
  });
}
function updateCarousel() {
  clearInterval(timer);
  const slides = [...document.querySelectorAll<HTMLElement>('[data-slide]')];
  slides.forEach((slide, i) => { slide.classList.toggle('active', i === currentSlide); slide.inert = i !== currentSlide; slide.setAttribute('aria-hidden', String(i !== currentSlide)); });
  document.querySelectorAll<HTMLButtonElement>('[data-slide-to]').forEach((dot, i) => { dot.classList.toggle('selected', i === currentSlide); dot.setAttribute('aria-current', String(i === currentSlide)); });
  document.querySelector('#slide-number')!.textContent = String(currentSlide + 1).padStart(2, '0');
  const pause = document.querySelector<HTMLButtonElement>('#hero-pause')!;
  pause.setAttribute('aria-pressed', String(paused || reduced.matches)); pause.setAttribute('aria-label', paused || reduced.matches ? 'Reanudar carrusel' : 'Pausar carrusel');
  if (!loading && slides.length > 1 && !paused && !hovering && !focusing && !reduced.matches && !document.hidden) timer = setInterval(() => changeSlide(1), 6500);
}
function changeSlide(delta: number) {
  const count = document.querySelectorAll('[data-slide]').length;
  if (!count) return;
  currentSlide = (currentSlide + delta + count) % count; updateCarousel();
  if (!reduced.matches) gsap.fromTo(document.querySelector('.hero-slide.active .hero-content'), { opacity: 0, y: 12 }, { opacity: 1, y: 0, duration: .35, clearProps: 'all' });
}
async function loadCatalog(targetPage = 0) {
  page = targetPage; loading = true; clearInterval(timer);
  previous.disabled = true; next.disabled = true; state.hidden = false; retry.hidden = true; error.hidden = true; state.setAttribute('aria-busy', 'true');
  document.querySelector<HTMLElement>('.hero')!.hidden = true;
  document.querySelectorAll<HTMLElement>('[data-events-section]').forEach(section => section.hidden = true);
  const dialog = document.querySelector<HTMLDialogElement>('#event-dialog')!; if (dialog.open) dialog.close();
  document.querySelector('#catalog-state-title')!.textContent = 'Buscando tu próximo plan…'; status.textContent = 'Consultando categoría y ciudad en el servidor.';
  pageLabel.textContent = 'Cargando página ' + (page + 1) + '…'; document.querySelector('#filter-status')!.textContent = 'El texto se aplicará a la nueva página cuando termine de cargar.';
  document.querySelector<HTMLElement>('#clear-filters')!.hidden = !query.value && !city.value && !tipo;
  try {
    const path = searchPath(tipo, city.value, targetPage);
    const response = await runLatest(signal => api(path, { signal }));
    if (!response.current) return;
    events = mapCatalog(response.data.data); loading = false;
    for (const event of events) if (event.city) cities.add(event.city);
    const options = document.querySelector('#catalog-cities')!;
    options.replaceChildren(...[...cities].sort((a, b) => a.localeCompare(b, 'es')).map(value => new Option(value, value)));
    renderCatalog();
    state.hidden = events.length > 0;
    if (!events.length) {
      document.querySelector('#catalog-state-title')!.textContent = 'No hay resultados en esta página.';
      status.textContent = page > 0 ? 'Volvé a la página anterior o cambiá los filtros. No se informa un total de páginas.' : 'No hay eventos publicados que coincidan con la categoría y ciudad consultadas.';
    }
    pageLabel.textContent = 'Página ' + (page + 1) + ' · ' + events.length + ' resultados recibidos';
    previous.disabled = page === 0; next.disabled = events.length < pageSize;
    state.setAttribute('aria-busy', 'false'); updateCarousel();
  } catch (cause) {
    loading = false; events = []; state.setAttribute('aria-busy', 'false');
    document.querySelector('#catalog-state-title')!.textContent = 'No pudimos cargar los eventos.';
    status.textContent = 'Reintentá la misma página o cambiá los filtros.';
    error.textContent = cause instanceof Error ? cause.message : 'No se pudo leer la respuesta del servidor.'; error.hidden = false; retry.hidden = false;
    pageLabel.textContent = 'Página ' + (page + 1) + ' sin cargar'; previous.disabled = page === 0; next.disabled = true;
    document.querySelector('#filter-status')!.textContent = 'No hay una página cargada para filtrar por texto.';
  }
}

retry.addEventListener('click', () => loadCatalog(page));
previous.addEventListener('click', () => { if (!loading && page > 0) loadCatalog(page - 1); });
next.addEventListener('click', () => { if (!loading && !next.disabled) loadCatalog(page + 1); });
query.addEventListener('input', () => { if (!loading) renderCatalog(); });
city.addEventListener('change', () => loadCatalog(0));
document.querySelector('.search-form')!.addEventListener('submit', event => { event.preventDefault(); loadCatalog(0); });
document.querySelectorAll<HTMLButtonElement>('[data-category]').forEach(button => button.addEventListener('click', () => {
  tipo = button.dataset.category || '';
  document.querySelectorAll<HTMLButtonElement>('[data-category]').forEach(other => { other.classList.toggle('chosen', other === button); other.setAttribute('aria-pressed', String(other === button)); });
  loadCatalog(0);
}));
document.querySelector('#clear-filters')!.addEventListener('click', () => {
  query.value = ''; city.value = ''; tipo = '';
  document.querySelectorAll<HTMLButtonElement>('[data-category]').forEach(button => { const selected = !button.dataset.category; button.classList.toggle('chosen', selected); button.setAttribute('aria-pressed', String(selected)); });
  loadCatalog(0);
});
const hero = document.querySelector<HTMLElement>('.hero')!;
document.querySelector('#hero-next')!.addEventListener('click', () => changeSlide(1));
document.querySelector('#hero-prev')!.addEventListener('click', () => changeSlide(-1));
document.querySelector('#hero-pause')!.addEventListener('click', () => { paused = !paused; updateCarousel(); });
hero.addEventListener('mouseenter', () => { hovering = true; updateCarousel(); });
hero.addEventListener('mouseleave', () => { hovering = false; updateCarousel(); });
hero.addEventListener('focusin', () => { focusing = true; updateCarousel(); });
hero.addEventListener('focusout', () => { focusing = hero.contains(document.activeElement); setTimeout(() => { focusing = hero.contains(document.activeElement); updateCarousel(); }, 0); });
hero.addEventListener('keydown', event => { if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') { event.preventDefault(); changeSlide(event.key === 'ArrowRight' ? 1 : -1); } });
let touchX = 0, touchY = 0;
hero.addEventListener('touchstart', event => { touchX = event.changedTouches[0].clientX; touchY = event.changedTouches[0].clientY; }, { passive: true });
hero.addEventListener('touchend', event => { const dx = event.changedTouches[0].clientX - touchX, dy = event.changedTouches[0].clientY - touchY; if (Math.abs(dx) > 55 && Math.abs(dx) > Math.abs(dy)) changeSlide(dx < 0 ? 1 : -1); }, { passive: true });
document.addEventListener('visibilitychange', updateCarousel); reduced.addEventListener('change', updateCarousel);
document.querySelectorAll<HTMLElement>('[data-events-section]').forEach(section => {
  const rail = section.querySelector<HTMLElement>('.event-rail')!;
  for (const [selector, direction] of [['.rail-prev', -1], ['.rail-next', 1]] as const) section.querySelector(selector)!.addEventListener('click', () => rail.scrollBy({ left: direction * rail.clientWidth * .8, behavior: reduced.matches ? 'instant' : 'smooth' }));
  rail.addEventListener('scroll', updateRails, { passive: true }); new ResizeObserver(updateRails).observe(rail);
});
window.addEventListener('resize', updateRails);
const menu = document.querySelector<HTMLButtonElement>('.mobile-toggle')!, mobileNav = document.querySelector<HTMLElement>('#mobile-nav')!;
menu.addEventListener('click', () => { mobileNav.hidden = !mobileNav.hidden; menu.setAttribute('aria-expanded', String(!mobileNav.hidden)); });
mobileNav.querySelectorAll('a').forEach(a => a.addEventListener('click', () => { mobileNav.hidden = true; menu.setAttribute('aria-expanded', 'false'); }));
const dialog = document.querySelector<HTMLDialogElement>('#event-dialog')!;
dialog.querySelectorAll('.dialog-close').forEach(button => button.addEventListener('click', () => dialog.close()));
dialog.addEventListener('click', event => { const r = dialog.getBoundingClientRect(); if (event.target === dialog && (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom)) dialog.close(); });
let toastTimer: ReturnType<typeof setTimeout>;
document.addEventListener('click', e => {
  const target = e.target as Element;
  const dot = target.closest<HTMLElement>('[data-slide-to]'); if (dot) { currentSlide = Number(dot.dataset.slideTo); updateCarousel(); return; }
  const favorite = target.closest<HTMLElement>('[data-favorite]');
  if (favorite) {
    const id = Number(favorite.dataset.favorite), exists = favorites.includes(id), values = exists ? favorites.filter(value => value !== id) : [...favorites, id];
    const toast = document.querySelector('#toast')!;
    try { localStorage.setItem('ticketya-favorites', JSON.stringify(values)); favorites = values; updateFavorites(); toast.textContent = exists ? 'Evento quitado de favoritos de este dispositivo' : 'Evento guardado en este dispositivo'; }
    catch { toast.textContent = 'No pudimos guardar el favorito en este dispositivo.'; }
    toast.classList.add('visible'); clearTimeout(toastTimer); toastTimer = setTimeout(() => toast.classList.remove('visible'), 2500); return;
  }
  const link = target.closest<HTMLElement>('[data-event]'); if (!link) return;
  e.preventDefault(); const event = events.find(event => event.id === Number(link.dataset.event)); if (!event || loading) return;
  const bindings: Record<string, string> = { 'event-dialog-title': event.title, 'dialog-type': event.category, 'dialog-description': event.description,
    'dialog-date': event.fullDate, 'dialog-location': [event.location, event.address].filter(Boolean).join(' · '), 'dialog-artists': event.artist, 'dialog-state': 'Estado del evento: ' + event.state };
  for (const [id, value] of Object.entries(bindings)) document.getElementById(id)!.textContent = value;
  document.querySelector<HTMLAnchorElement>('#event-selection-link')!.href = eventLink(event.id);
  dialog.showModal();
});
loadCatalog();
