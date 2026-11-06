import gsap from 'gsap';

const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
const slides = [...document.querySelectorAll<HTMLElement>('[data-slide]')];
const dots = [...document.querySelectorAll<HTMLButtonElement>('[data-slide-to]')];
const viewport = document.querySelector<HTMLElement>('.hero-viewport')!;
const pause = document.querySelector<HTMLButtonElement>('#hero-pause')!;
let current = 0, busy = false, manualPause = false, hoverPause = false, focusPause = false;
function showSlide(next: number) {
  if (busy || next === current) return;
  next = (next + slides.length) % slides.length;
  if (next === current) return;
  const outgoing = slides[current], incoming = slides[next];
  busy = true;
  outgoing.inert = true; outgoing.setAttribute('aria-hidden', 'true');
  incoming.inert = false; incoming.setAttribute('aria-hidden', 'false');
  incoming.classList.add('active');
  gsap.set(incoming, { zIndex: 2 }); gsap.set(outgoing, { zIndex: 1 });
  gsap.fromTo(incoming, { opacity: 0 }, {
    opacity: 1, duration: reduced.matches ? 0 : .65,
    onComplete() { outgoing.classList.remove('active'); gsap.set(outgoing, { opacity: 0 }); busy = false; }
  });
  if (!reduced.matches) {
    gsap.fromTo(incoming.querySelector('.hero-photo'), { scale: 1.08, xPercent: 2 }, { scale: 1, xPercent: 0, duration: 1.3, ease: 'power3.out' });
    gsap.fromTo(incoming.querySelectorAll('.hero-content > *'), { y: 25, opacity: 0 }, { y: 0, opacity: 1, stagger: .07, duration: .6, delay: .12, ease: 'power3.out' });
  }
  current = next;
  dots.forEach((dot, i) => { dot.classList.toggle('selected', i === next); dot.setAttribute('aria-current', String(i === next)); });
  document.querySelector('#slide-number')!.textContent = String(next + 1).padStart(2, '0');
}
let timer: ReturnType<typeof setInterval>;
function resetTimer() {
  clearInterval(timer);
  if (!manualPause && !hoverPause && !focusPause && !reduced.matches && !document.hidden) timer = setInterval(() => showSlide(current + 1), 6500);
  pause.setAttribute('aria-pressed', String(manualPause || reduced.matches));
  pause.setAttribute('aria-label', manualPause || reduced.matches ? 'Reanudar carrusel' : 'Pausar carrusel');
}
document.querySelector('#hero-next')!.addEventListener('click', () => { showSlide(current + 1); resetTimer(); });
document.querySelector('#hero-prev')!.addEventListener('click', () => { showSlide(current - 1); resetTimer(); });
dots.forEach((dot, i) => dot.addEventListener('click', () => { showSlide(i); resetTimer(); }));
pause.addEventListener('click', () => { manualPause = !manualPause; resetTimer(); });
const hero = document.querySelector<HTMLElement>('.hero')!;
hero.addEventListener('mouseenter', () => { hoverPause = true; resetTimer(); });
hero.addEventListener('mouseleave', () => { hoverPause = false; resetTimer(); });
hero.addEventListener('focusin', () => { focusPause = true; resetTimer(); });
hero.addEventListener('focusout', () => { setTimeout(() => { focusPause = hero.contains(document.activeElement); resetTimer(); }, 0); });
viewport.addEventListener('keydown', e => { if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') { e.preventDefault(); showSlide(current + (e.key === 'ArrowRight' ? 1 : -1)); resetTimer(); } });
let touchX = 0, touchY = 0;
viewport.addEventListener('touchstart', e => { touchX = e.changedTouches[0].clientX; touchY = e.changedTouches[0].clientY; }, { passive: true });
viewport.addEventListener('touchend', e => { const dx = e.changedTouches[0].clientX - touchX, dy = e.changedTouches[0].clientY - touchY; if (Math.abs(dx) > 55 && Math.abs(dx) > Math.abs(dy)) { showSlide(current + (dx < 0 ? 1 : -1)); resetTimer(); } }, { passive: true });
document.addEventListener('visibilitychange', resetTimer); reduced.addEventListener('change', resetTimer); resetTimer();

const sections = [...document.querySelectorAll<HTMLElement>('[data-events-section]')];
sections.forEach(section => {
  const rail = section.querySelector<HTMLElement>('.event-rail')!;
  const prev = section.querySelector<HTMLButtonElement>('.rail-prev')!, next = section.querySelector<HTMLButtonElement>('.rail-next')!;
  function update() { prev.disabled = rail.scrollLeft <= 2; next.disabled = rail.scrollLeft + rail.clientWidth >= rail.scrollWidth - 3; }
  prev.addEventListener('click', () => rail.scrollBy({ left: -rail.clientWidth * .8, behavior: reduced.matches ? 'instant' : 'smooth' }));
  next.addEventListener('click', () => rail.scrollBy({ left: rail.clientWidth * .8, behavior: reduced.matches ? 'instant' : 'smooth' }));
  rail.addEventListener('scroll', update, { passive: true }); window.addEventListener('resize', update); new ResizeObserver(update).observe(rail); update();
});
const normalize = (s: string) => s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
let category = 'Todos';
const query = document.querySelector<HTMLInputElement>('#query')!, city = document.querySelector<HTMLSelectElement>('#city')!;
function filter() {
  const text = normalize(query.value.trim());
  const ids = new Set<string>();
  sections.forEach(section => {
    let count = 0;
    section.querySelectorAll<HTMLElement>('[data-card]').forEach(card => {
      const matches = normalize(card.dataset.title!).includes(text) && (!city.value || card.dataset.city === city.value) && (category === 'Todos' || card.dataset.type === category);
      card.hidden = !matches;
      if (matches) { count++; ids.add(card.querySelector<HTMLElement>('[data-event]')!.dataset.event!); }
    });
    section.querySelector<HTMLElement>('.empty-state')!.hidden = count > 0;
    section.querySelector<HTMLElement>('.event-rail')!.scrollLeft = 0;
    section.querySelectorAll<HTMLButtonElement>('.rail-controls button').forEach(b => b.disabled = count === 0);
  });
  const active = !!text || !!city.value || category !== 'Todos';
  document.querySelector('#filter-status')!.textContent = active ? `${ids.size} ${ids.size === 1 ? 'evento encontrado' : 'eventos encontrados'}` : '';
  document.querySelector<HTMLElement>('#clear-filters')!.hidden = !active;
  window.dispatchEvent(new Event('resize'));
}
document.querySelector('.search-form')!.addEventListener('submit', e => { e.preventDefault(); filter(); document.querySelector('#alta-demanda')!.scrollIntoView({ behavior: reduced.matches ? 'instant' : 'smooth', block: 'start' }); });
city.addEventListener('change', filter); query.addEventListener('input', filter);
document.querySelectorAll<HTMLButtonElement>('[data-category]').forEach(button => button.addEventListener('click', () => {
  category = button.dataset.category!;
  document.querySelectorAll<HTMLButtonElement>('[data-category]').forEach(b => { b.classList.toggle('chosen', b === button); b.setAttribute('aria-pressed', String(b === button)); }); filter();
}));
document.querySelector('#clear-filters')!.addEventListener('click', () => { query.value = ''; city.value = ''; document.querySelector<HTMLButtonElement>('[data-category="Todos"]')!.click(); });

const menu = document.querySelector<HTMLButtonElement>('.mobile-toggle')!, mobileNav = document.querySelector<HTMLElement>('#mobile-nav')!;
menu.addEventListener('click', () => { mobileNav.hidden = !mobileNav.hidden; menu.setAttribute('aria-expanded', String(!mobileNav.hidden)); });
mobileNav.querySelectorAll('a').forEach(a => a.addEventListener('click', () => { mobileNav.hidden = true; menu.setAttribute('aria-expanded', 'false'); }));

type EventData = { id: number; title: string; artist: string; category: string; fullDate: string; venue: string; city: string; seating: string; price: number; image: string };
const events: EventData[] = JSON.parse(document.querySelector('#events-data')!.textContent!);
document.querySelectorAll<HTMLDialogElement>('dialog').forEach(dialog => {
  dialog.querySelectorAll('.dialog-close').forEach(button => button.addEventListener('click', () => dialog.close()));
  dialog.addEventListener('click', e => { const r = dialog.getBoundingClientRect(); if (e.target === dialog && (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom)) dialog.close(); });
});
let favorites: number[] = [];
try { const data: unknown = JSON.parse(localStorage.getItem('ticketya-favorites') || '[]'); if (Array.isArray(data)) favorites = data.filter((v): v is number => typeof v === 'number'); } catch { }
function updateFavorites() { document.querySelectorAll<HTMLButtonElement>('[data-favorite]').forEach(button => { const selected = favorites.includes(Number(button.dataset.favorite)); button.setAttribute('aria-pressed', String(selected)); const title = events.find(e => e.id === Number(button.dataset.favorite))?.title; button.setAttribute('aria-label', `${selected ? 'Quitar' : 'Guardar'} ${title} ${selected ? 'de' : 'en'} favoritos`); }); }
let toastTimer: ReturnType<typeof setTimeout>;
document.querySelectorAll<HTMLButtonElement>('[data-favorite]').forEach(button => button.addEventListener('click', () => {
  const id = Number(button.dataset.favorite); const existed = favorites.includes(id); favorites = existed ? favorites.filter(v => v !== id) : [...favorites, id];
  try { localStorage.setItem('ticketya-favorites', JSON.stringify(favorites)); } catch { }
  updateFavorites(); const toast = document.querySelector('#toast')!; toast.textContent = existed ? 'Evento quitado de tus favoritos' : 'Evento guardado en este dispositivo'; toast.classList.add('visible'); clearTimeout(toastTimer); toastTimer = setTimeout(() => toast.classList.remove('visible'), 2500);
})); updateFavorites();
if (!reduced.matches) {
  gsap.from('.hero-content > *', { opacity: 0, y: 28, duration: .7, stagger: .08, ease: 'power3.out', clearProps: 'all' });
  const observer = new IntersectionObserver(entries => entries.forEach(entry => { if (entry.isIntersecting) { gsap.from(entry.target.querySelectorAll('.section-heading, .event-card'), { opacity: 0, y: 30, duration: .6, stagger: .07, ease: 'power3.out', clearProps: 'all' }); observer.unobserve(entry.target); } }), { threshold: .12 });
  sections.forEach(section => observer.observe(section));
}
