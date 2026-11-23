import { api } from './api';
import { getSelectedProfileId } from './session';
import { parseHistory, pendingReservation, resumeLink, type Purchase } from './history-data';
export function initHistory(owner: number) {
  const list = document.querySelector<HTMLElement>('#purchase-list')!;
  const status = document.querySelector<HTMLElement>('#history-status')!;
  const refresh = document.querySelector<HTMLButtonElement>('#refresh-history')!;
  const dialog = document.querySelector<HTMLDialogElement>('#purchase-dialog')!;
  let purchases: Purchase[] = [], filter = '', version = 0;
  const reservations = new Map<number, { text: string; active: boolean }>();
  const titles = new Map<number, string>();
  const amount = (value: number) => value.toLocaleString('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const element = (tag: string, value: string, className = '') => { const node = document.createElement(tag); node.textContent = value; node.className = className; return node; };
  function details(p: Purchase) {
    document.querySelector<HTMLElement>('#history-entry-detail')!.dataset.purchaseTickets = String(p.id);
    document.querySelector('#purchase-title')!.textContent = titles.get(p.idEvento) || 'Evento #' + p.idEvento;
    document.querySelector('#purchase-detail')!.textContent = 'Compra #' + p.id + ' · ' + p.estadoCompra + ' · Evento #' + p.idEvento + ' · Sector #' + p.idSector + ' · ' + p.cantidadEntradas + ' entradas' + (p.idsAsientos.length ? ' · IDs de asientos: ' + p.idsAsientos.join(', ') : ' · sin asiento asignado') + ' · ' + (p.descripcion || 'Sin descripción');
    document.querySelector('#purchase-payment')!.textContent = 'Medio: ' + p.medioDePago + ' · Precio unitario: ' + amount(p.precioUnitario) + ' · Referencia de pago: ' + (p.referenciaPago || 'sin referencia') + ' · Reintegro: ' + (p.estadoReintegro || 'sin estado informado') + (p.referenciaReintegro ? ' · ' + p.referenciaReintegro : '');
    document.querySelector('#purchase-total')!.textContent = amount(p.importeTotal);
    document.querySelector('#history-reservation-detail')!.textContent = reservations.get(p.id)?.text || (p.estadoCompra === 'PENDIENTE' ? 'Reserva sin verificar.' : 'Reserva #' + p.idReserva);
    const link = document.querySelector<HTMLAnchorElement>('#history-resume-detail')!;
    link.hidden = p.estadoCompra !== 'PENDIENTE' || !reservations.get(p.id)?.active; link.href = resumeLink(p);
  }
  function paint() {
    if (getSelectedProfileId() !== owner) return;
    list.replaceChildren();
    const visible = purchases.filter(p => !filter || p.estadoCompra === filter);
    document.querySelectorAll('[data-purchase-count]').forEach(node => node.textContent = String(purchases.length));
    for (const p of visible) {
      const card = document.createElement('article'); card.className = 'purchase-card';
      const image = document.createElement('img'); image.src = '/event-placeholder.svg'; image.alt = 'Imagen ilustrativa de respaldo'; image.loading = 'lazy';
      const info = document.createElement('div'); info.className = 'purchase-info';
      const meta = element('div', 'Compra #' + p.id + ' · ' + p.estadoCompra, 'purchase-meta');
      info.append(meta, element('h3', titles.get(p.idEvento) || 'Evento #' + p.idEvento), element('p', 'Sector #' + p.idSector + ' · ' + p.cantidadEntradas + ' entradas · ' + (p.descripcion || 'Sin descripción')));
      if (p.estadoCompra === 'PENDIENTE') info.append(element('p', reservations.get(p.id)?.text || 'Consultando reserva…'));
      const footer = element('div', '', 'purchase-bottom'); footer.append(element('strong', amount(p.importeTotal)));
      const detail = element('button', 'Ver detalle ↗'); detail.addEventListener('click', () => { details(p); dialog.dataset.purchaseId = String(p.id); dialog.showModal(); }); footer.append(detail);
      const tickets = element('button', 'Ver entradas ↗'); tickets.dataset.purchaseTickets = String(p.id); footer.append(tickets);
      if (p.estadoCompra === 'PENDIENTE' && reservations.get(p.id)?.active) { const link = element('a', 'Retomar pago simulado ↗') as HTMLAnchorElement; link.href = resumeLink(p); footer.append(link); }
      info.append(footer); card.append(image, info); list.append(card);
    }
    if (!visible.length) list.append(element('p', purchases.length ? 'No hay compras con ese estado.' : 'Este perfil todavía no tiene compras.', 'profile-empty'));
    if (dialog.open) { const p = purchases.find(p => p.id === Number(dialog.dataset.purchaseId)); if (p) details(p); else dialog.close(); }
  }
  async function load() {
    const requestVersion = ++version;
    refresh.disabled = true; purchases = []; list.replaceChildren(); status.textContent = 'Cargando compras del perfil…'; list.setAttribute('aria-busy', 'true'); reservations.clear(); titles.clear();
    if (dialog.open) dialog.close();
    try {
      const data = parseHistory((await api('/compras/cliente/' + owner, { cache: 'no-store' })).data, owner);
      if (requestVersion !== version || getSelectedProfileId() !== owner) return;
      purchases = data; paint(); status.textContent = 'Consultando detalles y reservas pendientes…';
      const valid = () => requestVersion === version && getSelectedProfileId() === owner;
      await Promise.all([
        ...[...new Set(data.map(p => p.idEvento))].map(async eventId => {
          try { const { data: event } = await api<{ id: number; nombre: string }>('/api/eventos/' + eventId, { cache: 'no-store' });
            if (valid() && event?.id === eventId && typeof event.nombre === 'string') titles.set(eventId, event.nombre);
          } catch { /* Eventos borrados o no publicados siguen visibles por ID en el historial. */ }
        }),
        ...data.filter(p => p.estadoCompra === 'PENDIENTE').map(async p => {
          try {
            const r = pendingReservation((await api('/reservas/' + p.idReserva, { cache: 'no-store' })).data, p);
            if (valid()) reservations.set(p.id, { active: r.estado === 'ACTIVA' && Date.parse(r.venceEn) > Date.now(), text: 'Reserva #' + r.id + ' · ' + r.estado + ' · vence: ' + new Date(r.venceEn).toLocaleString('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' }) + ' (Buenos Aires). La compra sigue ' + p.estadoCompra + '.' });
          } catch (cause) { if (valid()) reservations.set(p.id, { active: false, text: 'Reserva sin verificar: ' + (cause instanceof Error ? cause.message : 'falló la consulta') }); }
        })
      ]);
      if (valid()) { paint(); status.textContent = 'Historial del servidor. Los importes no incluyen moneda en el contrato. El pago y los reintegros son simulados. Actualizá para consultar cambios de estado.'; }
    } catch (cause) {
      if (requestVersion !== version || getSelectedProfileId() !== owner) return;
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
  document.addEventListener('visibilitychange', () => { if (!document.hidden && getSelectedProfileId() === owner) load(); });
  load();
}
