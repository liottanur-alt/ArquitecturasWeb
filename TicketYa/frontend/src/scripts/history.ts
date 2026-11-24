import { api } from './api';
import { mapCatalog, setEventImage } from './catalog-data';
import { getAuthenticatedUserId } from './session';
import { parseHistory, resumeLink, type Purchase } from './history-data';
export function initHistory(owner: number) {
  const list = document.querySelector<HTMLElement>('#purchase-list')!;
  const status = document.querySelector<HTMLElement>('#history-status')!;
  const refresh = document.querySelector<HTMLButtonElement>('#refresh-history')!;
  const dialog = document.querySelector<HTMLDialogElement>('#purchase-dialog')!;
  let purchases: Purchase[] = [], filter = '', version = 0;
  const events = new Map<number, ReturnType<typeof mapCatalog>[number]>();
  const eventTitle = (id: number) => events.get(id)?.title || 'Evento #' + id;
  const amount = (value: number) => value.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const dateFormat = new Intl.DateTimeFormat('es-AR', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Argentina/Buenos_Aires' });
  const purchaseDate = (purchase: Purchase) => purchase.fechaCreacion ? dateFormat.format(Date.parse(purchase.fechaCreacion)) : 'Fecha de compra no informada';
  const element = (tag: string, value: string, className = '') => { const node = document.createElement(tag); node.textContent = value; node.className = className; return node; };
  function details(p: Purchase) {
    const entries = document.querySelector<HTMLElement>('#history-entry-detail')!;
    entries.dataset.purchaseTickets = String(p.id);
    entries.hidden = p.estadoCompra !== 'APROBADA' && p.estadoCompra !== 'CANCELADA';
    document.querySelector('#purchase-title')!.textContent = eventTitle(p.idEvento);
    document.querySelector('#purchase-detail')!.textContent = 'Compra #' + p.id + ' · ' + purchaseDate(p) + ' · ' + p.estadoCompra + ' · Sector #' + p.idSector + ' · ' + p.cantidadEntradas + ' entradas' + (p.idsAsientos.length ? ' · IDs de asientos: ' + p.idsAsientos.join(', ') : ' · ubicación libre');
    document.querySelector('#purchase-payment')!.textContent = 'Medio: ' + p.medioDePago + ' · Precio unitario: ' + amount(p.precioUnitario) + ' · Referencia de pago: ' + (p.referenciaPago || 'sin referencia') + ' · Reintegro: ' + (p.estadoReintegro || 'sin estado informado') + (p.referenciaReintegro ? ' · ' + p.referenciaReintegro : '');
    document.querySelector('#purchase-total')!.textContent = amount(p.importeTotal);
    const link = document.querySelector<HTMLAnchorElement>('#history-resume-detail')!;
    link.hidden = p.estadoCompra !== 'PENDIENTE'; link.href = resumeLink(p);
  }
  function paint() {
    if (getAuthenticatedUserId() !== owner) return;
    list.replaceChildren();
    const visible = purchases.filter(p => !filter || p.estadoCompra === filter);
    document.querySelectorAll('[data-purchase-count]').forEach(node => node.textContent = String(purchases.length));
    for (const p of visible) {
      const card = document.createElement('article'); card.className = 'purchase-card';
      const image = document.createElement('img'); image.alt = ''; image.loading = 'lazy';
      setEventImage(image, events.get(p.idEvento)?.image ?? '/event-placeholder.svg');
      const info = document.createElement('div'); info.className = 'purchase-info';
      const meta = element('div', '', 'purchase-meta');
      const badge = element('span', p.estadoCompra, 'wallet-badge');
      badge.dataset.state = p.estadoCompra;
      meta.append(element('span', 'Compra #' + p.id), badge);
      info.append(meta, element('h3', eventTitle(p.idEvento)), element('p', purchaseDate(p)), element('p', 'Sector #' + p.idSector + ' · ' + p.cantidadEntradas + ' entradas · ' + p.medioDePago));
      const footer = element('div', '', 'purchase-bottom'); footer.append(element('strong', amount(p.importeTotal)));
      const detail = element('button', p.referenciaPago ? 'Ver comprobante ↗' : 'Ver detalle ↗'); detail.addEventListener('click', () => { details(p); dialog.dataset.purchaseId = String(p.id); dialog.showModal(); }); footer.append(detail);
      if (p.estadoCompra === 'APROBADA' || p.estadoCompra === 'CANCELADA') {
        const tickets = element('button', 'Ver entradas ↗'); tickets.dataset.purchaseTickets = String(p.id); footer.append(tickets);
      }
      if (p.estadoCompra === 'PENDIENTE') { const link = element('a', 'Revisar compra pendiente ↗') as HTMLAnchorElement; link.href = resumeLink(p); footer.append(link); }
      info.append(footer); card.append(image, info); list.append(card);
    }
    if (!visible.length) {
      const empty = element('div', '', 'profile-empty');
      empty.append(element('p', purchases.length ? 'No hay compras con ese estado.' : 'Todavía no tenés compras.'));
      if (!purchases.length) {
        const explore = element('a', 'Explorar próximos eventos', 'button button-blue') as HTMLAnchorElement;
        explore.href = '/#explorar'; empty.append(explore);
      }
      list.append(empty);
    }
    if (dialog.open) { const p = purchases.find(p => p.id === Number(dialog.dataset.purchaseId)); if (p) details(p); else dialog.close(); }
  }
  async function load() {
    if (getAuthenticatedUserId() !== owner) return;
    const requestVersion = ++version;
    refresh.disabled = true; purchases = []; list.replaceChildren(); status.textContent = 'Cargando tus compras…'; list.setAttribute('aria-busy', 'true'); events.clear();
    if (dialog.open) dialog.close();
    try {
      const data = parseHistory((await api('/compras/cliente/' + owner, { cache: 'no-store' })).data, owner);
      if (requestVersion !== version || getAuthenticatedUserId() !== owner) return;
      purchases = data.sort((a, b) => (b.fechaCreacion ? Date.parse(b.fechaCreacion) : 0) - (a.fechaCreacion ? Date.parse(a.fechaCreacion) : 0) || b.id - a.id);
      status.textContent = 'Consultando los eventos de tus compras…';
      const valid = () => requestVersion === version && getAuthenticatedUserId() === owner;
      await Promise.all([...new Set(data.map(p => p.idEvento))].map(async eventId => {
        try {
          const { data } = await api('/api/eventos/' + eventId, { cache: 'no-store' });
          const [event] = mapCatalog([data]);
          if (valid() && event?.id === eventId) events.set(eventId, event);
        } catch { /* Los eventos que ya no están publicados siguen visibles por ID. */ }
      }));
      if (valid()) { paint(); status.textContent = 'Historial del servidor. Los importes no incluyen moneda en el contrato. El pago y los reintegros son simulados. Actualizá para consultar cambios de estado.'; }
    } catch (cause) {
      if (requestVersion !== version || getAuthenticatedUserId() !== owner) return;
      purchases = []; document.querySelectorAll('[data-purchase-count]').forEach(node => node.textContent = '—');
      status.textContent = cause instanceof Error ? cause.message : 'No pudimos consultar el historial. Reintentá.';
    } finally { if (requestVersion === version) { refresh.disabled = false; list.removeAttribute('aria-busy'); } }
  }
  document.querySelectorAll<HTMLButtonElement>('[data-history-filter]').forEach(button => button.addEventListener('click', () => {
    filter = button.dataset.historyFilter || '';
    document.querySelectorAll('[data-history-filter]').forEach(node => node.setAttribute('aria-pressed', String(node === button))); paint();
  }));
  refresh.addEventListener('click', load);
  window.addEventListener('pageshow', event => { if (event.persisted) load(); });
  document.addEventListener('visibilitychange', () => { if (!document.hidden && getAuthenticatedUserId() === owner) load(); });
  load();
}
