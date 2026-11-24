import { initRefunds } from './refunds';
import QRCode from 'qrcode';
import { api } from './api';
import { getAuthenticatedUserId } from './session';
import { parseEntry, parseEntries, qrEligible, filterEntry, type Entry } from './entry-data';
import { parsePurchase } from './history-data';
import { mapCatalog, setEventImage } from './catalog-data';
import { refundRule } from './refund-data';
export function initTickets() {
  const selectedOwner = getAuthenticatedUserId(); if (selectedOwner === null) return;
  const owner: number = selectedOwner;
  const list = document.querySelector<HTMLElement>('#ticket-list')!;
  const status = document.querySelector<HTMLElement>('#tickets-status')!;
  const dialog = document.querySelector<HTMLDialogElement>('#ticket-qr-dialog')!;
  const refresh = document.querySelector<HTMLButtonElement>('#refresh-tickets')!;
  const image = document.querySelector<HTMLImageElement>('#ticket-qr-image')!;
  let entries: Entry[] = [], filter = '', period = '', version = 0, qrVersion = 0;
  const refunds = initRefunds(owner, () => { load(); });
  const param = new URLSearchParams(location.search).get('compra');
  let purchaseId: number | null = param === null ? null : Number(param);
  const events = new Map<number, ReturnType<typeof mapCatalog>[number]>(), sectors = new Map<number,string>(), seats = new Map<number,string>();
  const eventDetails = new Map<number, { fechaHora?: string; estado?: string }>();
  const text = (tag: string, value: string, className = '') => { const node = document.createElement(tag); node.textContent = value; node.className = className; return node; };
  const sectorName = (entry: Entry) => (sectors.get(entry.idSector) || 'Sector') + ' #' + entry.idSector;
  const seatName = (entry: Entry) => entry.idAsiento === null ? 'Sin asiento asignado' : (seats.get(entry.idAsiento) || 'Asiento') + ' #' + entry.idAsiento;
  const startsAt = (entry: Entry) => Date.parse((eventDetails.get(entry.idEvento)?.fechaHora ?? '') + '-03:00');
  function paint() {
    if (getAuthenticatedUserId() !== owner) return;
    list.replaceChildren();
    document.querySelectorAll('[data-entry-count]').forEach(node => node.textContent = String(entries.length));
    document.querySelector('#valid-tickets')!.textContent = String(entries.filter(qrEligible).length);
    document.querySelector('#pending-tickets')!.textContent = String(entries.filter(e => e.estadoEntrada === 'SOLICITUD_DEVOLUCION').length);
    document.querySelector('#other-tickets')!.textContent = String(entries.filter(e => e.estadoQr === 'ESCANEADO').length);
    document.querySelector<HTMLElement>('#purchase-ticket-filter')!.hidden = purchaseId === null;
    document.querySelector('#purchase-ticket-label')!.textContent = purchaseId === null ? '' : 'Entradas de la compra #' + purchaseId;
    const now = Date.now();
    const visible = entries.filter(entry => {
      const starts = startsAt(entry);
      return filterEntry(entry, filter) && (!period || (Number.isFinite(starts) && (period === 'past' ? starts < now : starts >= now)));
    });
    for (const entry of visible) {
      const event = events.get(entry.idEvento), card = document.createElement('article'); card.className = 'entry-card';
      const photo = document.createElement('div'); photo.className = 'entry-photo';
      const img = document.createElement('img'); img.alt = ''; img.loading = 'lazy';
      setEventImage(img, event?.image ?? '/event-placeholder.svg'); photo.append(img);
      const content = document.createElement('div'); content.className = 'entry-content';
      const state = entry.estadoQr === 'ESCANEADO' && entry.estadoEntrada === 'PAGA' ? 'ESCANEADO' : entry.estadoEntrada;
      const label = { PAGA: startsAt(entry) < now ? 'Sin usar' : 'Activa', ESCANEADO: 'Utilizada',
        SOLICITUD_DEVOLUCION: 'Devolución pendiente', DEVOLUCION_ACEPTADA: 'Devuelta',
        DEVOLUCION_RECHAZADA: 'Devolución rechazada', EVENTO_CANCELADO: 'Evento cancelado' }[state];
      const meta = text('div', '', 'entry-topline');
      const badge = text('span', label ?? state, 'wallet-badge'); badge.dataset.state = state;
      meta.append(text('span', 'Entrada #' + entry.id), badge);
      content.append(meta, text('h3', event?.title || 'Evento #' + entry.idEvento), text('p', event?.fullDate || 'Fecha no disponible', 'entry-date'), text('p', event?.location || 'Lugar no disponible', 'entry-location'), text('p', sectorName(entry) + ' · ' + seatName(entry), 'entry-seat'));
      const actions = text('div', '', 'wallet-actions');
      const button = text('button', qrEligible(entry) ? 'Ver entrada / QR' : 'QR no disponible', 'entry-qr') as HTMLButtonElement; button.disabled = !qrEligible(entry); button.addEventListener('click', () => showQr(entry.id)); actions.append(button);
      const details = eventDetails.get(entry.idEvento);
      if (entry.idCliente === owner && Number.isFinite(startsAt(entry)) && refundRule(entry, [], details, now).allowed) {
        const refund = text('button', 'Solicitar devolución', 'entry-refund');
        refund.addEventListener('click', () => refunds.open(entry));
        actions.append(refund);
      }
      content.append(actions);
      const refundStatus = refunds.describe(entry); if (refundStatus) content.append(text('p', refundStatus, 'entry-note'));
      if (entry.estadoEntrada === 'DEVOLUCION_RECHAZADA') content.append(text('p', 'El backend no admite registrar ingreso tras una devolución rechazada. Consultá al organizador.', 'entry-note'));
      card.append(photo, content); list.append(card);
    }
    document.querySelector<HTMLElement>('#tickets-empty')!.hidden = visible.length > 0;
  }
  async function load() {
    if (getAuthenticatedUserId() !== owner) return;
    const current = ++version; qrVersion++; if (dialog.open) dialog.close(); image.removeAttribute('src'); image.hidden = true;
    entries = []; document.querySelectorAll('[data-entry-count], #valid-tickets, #pending-tickets, #other-tickets').forEach(node => node.textContent = '—'); list.replaceChildren(); events.clear(); eventDetails.clear(); sectors.clear(); seats.clear(); refresh.disabled = true; list.setAttribute('aria-busy','true');
    document.querySelector<HTMLElement>('#tickets-empty')!.hidden = true; status.textContent = 'Consultando entradas del servidor…';
    const valid = () => current === version && getAuthenticatedUserId() === owner;
    try {
      if (purchaseId !== null) {
        if (!Number.isSafeInteger(purchaseId) || purchaseId <= 0) throw new Error('ID de compra inválido. Podés volver a todas las entradas.');
        const p = parsePurchase((await api('/compras/' + purchaseId, { cache: 'no-store' })).data, owner);
        if (!valid()) return;
        if (p.id !== purchaseId) throw new Error('La compra consultada no coincide.');
      }
      const data = parseEntries((await api(purchaseId === null ? '/entradas/cliente/' + owner : '/entradas/compra/' + purchaseId, { cache: 'no-store' })).data, owner);
      if (!valid()) return; entries = data;
      await Promise.all([
        ...[...new Set(data.map(e => e.idEvento))].map(async id => {
          try {
            const { data: raw } = await api<{ fechaHora?: string; estado?: string }>('/api/eventos/' + id);
            const mapped = mapCatalog([raw])[0];
            if (raw && valid() && mapped.id === id) { events.set(id, mapped); eventDetails.set(id, raw); }
          } catch {}
        }),
        ...[...new Set(data.map(e => e.idSector))].map(async id => { try { const { data: sector } = await api<{id:number;nombre:string}>('/sectores/' + id); if (valid() && sector?.id === id && typeof sector.nombre === 'string') sectors.set(id, sector.nombre); } catch {} }),
        ...data.filter(e => e.idAsiento !== null).map(async e => { try { const { data: seat } = await api<{id:number;idSector:number;fila:string;columna:string}>('/asientos/' + e.idAsiento); if (valid() && seat?.id === e.idAsiento && seat.idSector === e.idSector && typeof seat.fila === 'string' && typeof seat.columna === 'string') seats.set(seat.id, 'Fila ' + seat.fila + ' · ' + seat.columna); } catch {} })
      ]);
      if (valid()) { paint(); status.textContent = 'Entradas del servidor' + (purchaseId === null ? ' para este perfil.' : ' para esta compra.') + ' Actualizá para consultar cambios. Esta vista no registra escaneos.'; }
    } catch (cause) { if (valid()) { status.textContent = cause instanceof Error ? cause.message : 'No pudimos consultar las entradas.'; document.querySelectorAll('[data-entry-count]').forEach(node => node.textContent = '—'); } }
    finally { if (valid()) { refresh.disabled = false; list.removeAttribute('aria-busy'); document.querySelector<HTMLElement>('#purchase-ticket-filter')!.hidden = purchaseId === null; } }
  }
  async function showQr(id: number) {
    if (getAuthenticatedUserId() !== owner) return;
    const current = ++qrVersion, dataVersion = version;
    image.hidden = true; image.removeAttribute('src');
    for (const id of ['qr-title','qr-location','qr-date','qr-ticket-id']) document.getElementById(id)!.textContent = '';
    const loading = document.querySelector<HTMLElement>('#qr-loading')!; loading.textContent = 'Verificando entrada y preparando QR…'; loading.hidden = false; dialog.showModal();
    const valid = () => current === qrVersion && dataVersion === version && dialog.open && getAuthenticatedUserId() === owner;
    try {
      const entry = parseEntry((await api('/entradas/' + id, { cache: 'no-store' })).data, owner);
      if (!valid()) return;
      const original = entries.find(e => e.id === id);
      if (!original || entry.id !== id || entry.idEvento !== original.idEvento || entry.idSector !== original.idSector || entry.idAsiento !== original.idAsiento) throw new Error('La entrada recibida no coincide con la elegida.');
      entries = entries.map(value => value.id === id ? entry : value); paint();
      document.querySelector('#qr-title')!.textContent = events.get(entry.idEvento)?.title || 'Evento #' + entry.idEvento;
      document.querySelector('#qr-location')!.textContent = [events.get(entry.idEvento)?.location, sectorName(entry), seatName(entry)].filter(Boolean).join(' · ');
      document.querySelector('#qr-date')!.textContent = events.get(entry.idEvento)?.fullDate || 'Fecha no disponible';
      document.querySelector('#qr-ticket-id')!.textContent = 'Entrada #' + entry.id + ' · ' + entry.estadoEntrada + ' · ' + entry.estadoQr;
      if (!qrEligible(entry)) throw new Error('El estado actual de la entrada no permite mostrar el QR para ingreso.');
      const url = await QRCode.toDataURL(entry.qr, { width: 320, margin: 4, errorCorrectionLevel: 'M', color: { dark: '#172039', light: '#ffffff' } });
      if (!valid()) return; image.src = url; image.hidden = false; loading.hidden = true;
    } catch (cause) { if (valid()) loading.textContent = cause instanceof Error ? cause.message : 'No pudimos preparar el QR.'; }
  }
  document.querySelectorAll<HTMLButtonElement>('[data-ticket-filter]').forEach(button => button.addEventListener('click', () => { filter = button.dataset.ticketFilter || ''; document.querySelectorAll('[data-ticket-filter]').forEach(node => node.setAttribute('aria-pressed', String(node === button))); paint(); }));
  const periods = document.querySelectorAll<HTMLButtonElement>('[data-ticket-period]');
  periods.forEach(button => button.addEventListener('click', () => {
    period = button.dataset.ticketPeriod ?? '';
    periods.forEach(control => control.setAttribute('aria-pressed', String(control === button)));
    paint();
  }));
  document.querySelector('#clear-purchase-ticket')!.addEventListener('click', () => { purchaseId = null; const url = new URL(location.href); url.searchParams.delete('compra'); history.replaceState(null,'',url.pathname + url.search + '#entradas'); load(); });
  document.addEventListener('click', event => { const target = (event.target as Element).closest<HTMLElement>('[data-purchase-tickets]'); if (!target) return; purchaseId = Number(target.dataset.purchaseTickets); filter = ''; document.querySelectorAll('[data-ticket-filter]').forEach(node => node.setAttribute('aria-pressed', String(!(node as HTMLElement).dataset.ticketFilter))); document.querySelector<HTMLDialogElement>('#purchase-dialog')!.close(); document.querySelector<HTMLButtonElement>('[data-tab="entradas"]')!.click(); load(); });
  document.querySelectorAll('[data-close-qr]').forEach(button => button.addEventListener('click', () => dialog.close()));
  dialog.addEventListener('close', () => { qrVersion++; image.hidden = true; image.removeAttribute('src'); });
  refresh.addEventListener('click', load);
  document.addEventListener('visibilitychange', () => { if (!document.hidden && getAuthenticatedUserId() === owner) load(); else { qrVersion++; if (dialog.open) dialog.close(); } });
  window.addEventListener('pageshow', event => { if (event.persisted) load(); });
  load();
}
