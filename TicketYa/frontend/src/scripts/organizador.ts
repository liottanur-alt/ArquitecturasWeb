import { api, ApiError } from './api';
import { getSession } from './session';

type Named = { id: number; nombre: string };
type OrganizerEvent = Named & { idOrganizador: number; lugar: Named | null; estadoPublicacion: string; fechaHora: string | null;
  estado?: string; descripcion?: string; tipo?: string; participantes?: Named[]; demanda?: boolean };
type Sector = Named & { idLugar: number; tipo: 'CAMPO' | 'NUMERADO'; capacidadTotal: number };
type Inventory = { idSector: number; precio: number; capacidad: number; cancelado: boolean };
type Sales = { entradasVendidas: number; entradasDevueltas: number; ingresosNetos: number; ocupacionPorcentaje: number | null };
type Report = Sales & { idEvento: number; capacidadTotal: number; sectores: (Sales & { idSector: number; capacidad: number })[] };

const element = <T extends HTMLElement = HTMLElement>(id: string) => document.getElementById(id) as T;
const workspace = element<HTMLFieldSetElement>('organizer-workspace');
const status = element('organizer-status');
const access = element('organizer-access');
const eventSelect = element<HTMLSelectElement>('organizer-events');
const numberedSelect = element<HTMLSelectElement>('organizer-numbered-sector');
const inventorySelect = element<HTMLSelectElement>('organizer-inventory-sector');
const placeSelect = element<HTMLSelectElement>('organizer-place-select');
const artistSelect = element<HTMLSelectElement>('organizer-artists-select');
const publish = element<HTMLButtonElement>('organizer-publish');
const session = getSession();
const active = () => session !== null && getSession()?.accessToken === session.accessToken;
const allowed = session?.roles.some(role => role === 'ORGANIZADOR' || role === 'ADMINISTRADOR');
const numberFormat = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 });

let events: OrganizerEvent[] = [];
let sectors: Sector[] = [];
let inventory: Inventory[] = [];
let editing: number | null = null;
let metricsVersion = 0;

const selectedEvent = () => events.find(event => event.id === Number(eventSelect.value));
const selectedSector = (id: number) => sectors.find(sector => sector.id === id);
const isDraft = (event: OrganizerEvent | undefined) => event?.estadoPublicacion === 'BORRADOR';
const id = (value: unknown) => Number.isSafeInteger(value) && Number(value) > 0;
const message = (cause: unknown) => cause instanceof Error ? cause.message : 'La operación no pudo confirmarse.';
const entityLabel = (entity: Named) => entity.nombre + ' · #' + entity.id;
const stateLabel = (event: OrganizerEvent) => ({ BORRADOR: 'Borrador', EN_CURSO: 'Publicado', FINALIZADO: 'Finalizado', CANCELADO: 'Cancelado', AGOTADO: 'Agotado' }[isDraft(event) ? 'BORRADOR' : event.estado ?? ''] ?? 'Publicado');

async function readReport(eventId: number) {
  const { data } = await api<Report>('/api/reportes/eventos/' + eventId, { cache: 'no-store' });
  if (!data || data.idEvento !== eventId || !Array.isArray(data.sectores)
    || !Number.isSafeInteger(data.entradasVendidas) || !Number.isFinite(data.ingresosNetos)) {
    throw new Error('El reporte recibido no corresponde al evento.');
  }
  return data;
}

async function refreshMetrics() {
  const version = ++metricsVersion;
  const reportable = events.filter(event => !isDraft(event));
  const hint = element('organizer-metrics-status');
  const sold = element('organizer-sold');
  const revenue = element('organizer-revenue');
  hint.textContent = reportable.length ? 'Consultando ventas y recaudación…' : '';
  sold.textContent = revenue.textContent = '—';
  let checked = 0, entries = 0, income = 0;
  for (let offset = 0; offset < reportable.length; offset += 4) {
    const results = await Promise.allSettled(reportable.slice(offset, offset + 4).map(event => readReport(event.id)));
    if (!active() || version !== metricsVersion) return;
    for (const result of results) {
      if (result.status !== 'fulfilled') continue;
      checked++;
      entries += result.value.entradasVendidas;
      income += result.value.ingresosNetos;
    }
  }
  if (checked || !reportable.length) {
    sold.textContent = numberFormat.format(entries);
    revenue.textContent = numberFormat.format(income);
  }
  hint.textContent = checked < reportable.length
    ? 'Datos disponibles para ' + checked + ' de ' + reportable.length + ' eventos. Actualizá los datos para reintentar.'
    : '';
}

function showCards() {
  element('organizer-active-count').textContent = String(events.filter(event => event.estadoPublicacion === 'PUBLICADO' && event.estado === 'EN_CURSO').length);
  const cards = element('organizer-event-cards');
  cards.replaceChildren();
  if (!events.length) {
    const empty = document.createElement('li');
    empty.textContent = 'Todavía no tenés eventos.';
    cards.append(empty);
  }
  for (const event of events) {
    const card = document.createElement('li');
    card.className = 'ops-event-card';
    const title = document.createElement('strong');
    title.textContent = event.nombre;
    const badge = document.createElement('span');
    badge.className = 'ops-badge ' + (isDraft(event) ? 'ops-badge-draft' : event.estado === 'EN_CURSO' ? 'ops-badge-active' : '');
    badge.textContent = stateLabel(event);
    const actions = document.createElement('div');
    actions.className = 'ops-actions';
    for (const action of isDraft(event) ? ['Editar', 'Sectores', 'Métricas'] : ['Métricas']) {
      const button = document.createElement('button');
      button.type = 'button'; button.className = 'ops-button ops-secondary'; button.textContent = action;
      button.addEventListener('click', () => {
        eventSelect.value = String(event.id);
        if (action === 'Editar') { editDraft(event); return; }
        void run('Consultando evento…', refreshEvent).then(() => {
          const target = action === 'Métricas' ? 'organizer-report-title' : 'organizer-events-title';
          element(target).scrollIntoView({ block: 'start' });
          if (action === 'Métricas') element<HTMLButtonElement>('organizer-report-load').click();
        });
      });
      actions.append(button);
    }
    const context = document.createElement('p');
    context.className = 'ops-event-context';
    const location = event.lugar?.nombre;
    const date = event.fechaHora?.replace('T', ' ').slice(0, 16);
    context.textContent = [location, date].filter(Boolean).join(' · ');
    card.append(badge, title, context, actions);
    cards.append(card);
  }
  void refreshMetrics();
}

function editDraft(event?: OrganizerEvent) {
  element<HTMLDetailsElement>('organizer-draft-panel').open = true;
  const form = element<HTMLFormElement>('organizer-draft');
  form.reset();
  editing = event?.id ?? null;
  element('organizer-draft-title').textContent = event ? 'Editar borrador' : 'Crear borrador';
  element('organizer-save-draft').textContent = event ? 'Guardar cambios' : 'Crear borrador';
  element('organizer-cancel-edit').hidden = !event;
  if (!event) return;
  for (const [name, value] of Object.entries({
    nombre: event.nombre, descripcion: event.descripcion ?? '', tipo: event.tipo ?? '',
    fechaHora: event.fechaHora?.slice(0, 16) ?? '', idLugar: String(event.lugar?.id ?? '')
  })) (form.elements.namedItem(name) as HTMLInputElement).value = value;
  (form.elements.namedItem('demanda') as HTMLInputElement).checked = event.demanda === true;
  const ids = new Set(event.participantes?.map(artist => artist.id));
  Array.from(artistSelect.options).forEach(option => { option.selected = ids.has(Number(option.value)); });
  element('organizer-draft-title').scrollIntoView({ block: 'start' });
  (form.elements.namedItem('nombre') as HTMLInputElement).focus();
}
function fillSelect<T extends Named>(select: HTMLSelectElement, values: T[], label: (value: T) => string = entityLabel) {
  const previous = new Set(Array.from(select.selectedOptions, option => option.value));
  const options = values.map(value => new Option(label(value), String(value.id), false, previous.has(String(value.id))));
  if (!select.multiple) options.unshift(new Option('Elegí una opción', ''));
  select.replaceChildren(...options);
}

async function readList<T>(path: string) {
  const { data } = await api<T[]>(path, { cache: 'no-store' });
  if (!Array.isArray(data)) throw new Error('El servidor devolvió una lista inválida.');
  return data;
}

async function write(path: string, method: 'POST' | 'PUT' | 'PATCH', body?: unknown) {
  if (!active()) throw new Error('La sesión cambió.');
  return (await api<Named>(path, { method, body })).data;
}

async function refreshCatalog() {
  const [places, artists, owned] = await Promise.all([
    readList<Named>('/api/lugares'),
    readList<Named>('/api/artistas'),
    readList<OrganizerEvent>('/api/eventos/mis-eventos')
  ]);
  if (!active()) return;
  events = owned.filter(event => event?.idOrganizador === session?.userId && id(event.id));
  fillSelect(placeSelect, places.filter(place => id(place?.id)));
  fillSelect(artistSelect, artists.filter(artist => id(artist?.id)));
  fillSelect(eventSelect, events, event => entityLabel(event) + ' · ' + event.estadoPublicacion);
  showCards();
}

function clearReport() {
  element('organizer-report-total').textContent = '';
  element('organizer-report-table').hidden = true;
  element('organizer-report-rows').replaceChildren();
}

async function refreshInventory(event: OrganizerEvent) {
  const summary = element('organizer-inventory-summary');
  inventory = [];
  publish.disabled = true;
  try {
    const rows = await readList<Inventory>('/eventos/' + event.id + '/sectores');
    if (!active()) return;
    inventory = rows;
    summary.textContent = rows.length ? rows.map(row => {
      const sector = selectedSector(row.idSector)?.nombre ?? 'Sector #' + row.idSector;
      return sector + ': ' + row.capacidad + ' lugares a ' + numberFormat.format(row.precio) + ' por entrada';
    }).join(' · ') : 'El inventario está inicializado. Asociá al menos un sector.';
    publish.disabled = !rows.some(row => !row.cancelado && row.capacidad > 0 && row.precio >= 0);
  } catch (cause) {
    if (!active()) return;
    if (cause instanceof ApiError && cause.status === 404) summary.textContent = 'El inventario todavía no está inicializado.';
    else summary.textContent = 'No se pudo consultar el inventario. ' + message(cause);
  }
}

async function refreshSeats() {
  const description = element('organizer-seats');
  const sector = selectedSector(Number(numberedSelect.value));
  description.textContent = '';
  const preview = element('organizer-seat-preview');
  const map = element('organizer-seat-map');
  preview.hidden = true;
  map.replaceChildren();
  if (!sector || sector.tipo !== 'NUMERADO') return;
  const seats = await readList<{ fila: string; columna: string }>('/asientos?idSector=' + sector.id);
  if (!active()) return;
  description.textContent = 'Asientos cargados: ' + seats.length;
  preview.hidden = seats.length === 0;
  const rows = new Map<string, HTMLElement>();
  const compare = new Intl.Collator('es-AR', { numeric: true });
  seats.sort((a, b) => compare.compare(a.fila, b.fila) || compare.compare(a.columna, b.columna)).forEach(seat => {
    let row = rows.get(seat.fila);
    if (!row) {
      row = document.createElement('div'); row.className = 'seat-map-row';
      const label = document.createElement('span'); label.className = 'seat-map-row-label'; label.textContent = 'Fila ' + seat.fila;
      row.append(label); rows.set(seat.fila, row); map.append(row);
    }
    const cell = document.createElement('span'); cell.className = 'seat-cell seat-configured';
    cell.textContent = seat.columna;
    cell.title = sector.nombre + ' · Fila ' + seat.fila + ' · Asiento ' + seat.columna;
    row.append(cell);
  });
}

async function refreshEvent(keepSector = false) {
  const event = selectedEvent();
  const previousNumbered = keepSector ? numberedSelect.value : '';
  const previousInventory = keepSector ? inventorySelect.value : '';
  clearReport();
  sectors = [];
  inventory = [];
  publish.disabled = true;
  element('organizer-draft-actions').hidden = !isDraft(event) || !event?.lugar;
  element<HTMLButtonElement>('organizer-report-load').disabled = !event;
  element('organizer-event-summary').textContent = event
    ? entityLabel(event) + ' · ' + (event.lugar?.nombre ?? 'Lugar sin informar') + ' · ' + (event.fechaHora?.replace('T', ' ') ?? 'Fecha sin informar')
    : 'Elegí un evento para continuar.';
  numberedSelect.replaceChildren();
  inventorySelect.replaceChildren();
  element('organizer-seats').textContent = '';
  element('organizer-seat-preview').hidden = true;
  element('organizer-inventory-summary').textContent = '';
  if (!event || !event.lugar || !isDraft(event)) return;
  const rows = await readList<Sector>('/sectores?idLugar=' + event.lugar.id);
  if (!active()) return;
  sectors = rows.filter(sector => sector?.idLugar === event.lugar?.id && id(sector.id));
  fillSelect(numberedSelect, sectors.filter(sector => sector.tipo === 'NUMERADO'));
  fillSelect(inventorySelect, sectors, sector => entityLabel(sector) + ' · ' + sector.tipo + ' · cupo físico ' + sector.capacidadTotal);
  if (Array.from(numberedSelect.options).some(option => option.value === previousNumbered)) numberedSelect.value = previousNumbered;
  if (Array.from(inventorySelect.options).some(option => option.value === previousInventory)) inventorySelect.value = previousInventory;
  await Promise.all([refreshSeats(), refreshInventory(event)]);
}

function textValue(data: FormData, name: string) {
  const value = String(data.get(name) ?? '').trim();
  if (!value) throw new Error('Completá los campos requeridos.');
  return value;
}

function numericValue(data: FormData, name: string) {
  const value = Number(textValue(data, name));
  if (!Number.isFinite(value)) throw new Error('Ingresá un número válido.');
  return value;
}

async function run(label: string, action: () => Promise<string | void>) {
  if (!active() || !allowed || workspace.disabled) return;
  workspace.disabled = true;
  status.textContent = label;
  try {
    const confirmation = await action();
    if (active()) status.textContent = confirmation || '';
  } catch (cause) {
    if (active()) status.textContent = message(cause);
  } finally {
    if (active()) workspace.disabled = false;
  }
}

async function afterWrite(confirmation: string, refresh: () => Promise<void>) {
  try { await refresh(); }
  catch (cause) { return confirmation + ' La actualización de la pantalla falló. ' + message(cause); }
  return confirmation;
}

function onSubmit(formId: string, label: string, action: (data: FormData, form: HTMLFormElement) => Promise<string>) {
  const form = element<HTMLFormElement>(formId);
  form.addEventListener('submit', event => {
    event.preventDefault();
    const data = new FormData(form);
    void run(label, () => action(data, form));
  });
}

function requireDraft() {
  const event = selectedEvent();
  if (!event?.lugar || !isDraft(event)) throw new Error('Elegí un borrador con lugar asignado.');
  return { event, placeId: event.lugar.id };
}

onSubmit('organizer-place', 'Guardando lugar…', async (data, form) => {
  const result = await write('/api/lugares', 'POST', {
    nombre: textValue(data, 'nombre'), direccion: textValue(data, 'direccion'), ciudad: textValue(data, 'ciudad'),
    tipo: textValue(data, 'tipo'), latitud: numericValue(data, 'latitud'), longitud: numericValue(data, 'longitud')
  });
  const placeId = result?.id;
  if (!id(placeId)) throw new Error('No se confirmó el ID del lugar. Actualizá los datos antes de volver a enviar.');
  form.reset();
  return afterWrite('Lugar creado.', async () => {
    await refreshCatalog();
    if (active()) placeSelect.value = String(placeId);
  });
});

onSubmit('organizer-artist', 'Guardando artista…', async (data, form) => {
  const result = await write('/api/artistas', 'POST', { nombre: textValue(data, 'nombre'), genero: textValue(data, 'genero') });
  const artistId = result?.id;
  if (!id(artistId)) throw new Error('No se confirmó el ID del artista. Actualizá los datos antes de volver a enviar.');
  form.reset();
  return afterWrite('Artista creado.', async () => {
    await refreshCatalog();
    const option = Array.from(artistSelect.options).find(value => Number(value.value) === artistId);
    if (active() && option) option.selected = true;
  });
});

onSubmit('organizer-draft', 'Guardando borrador…', async (data, form) => {
  const idLugar = numericValue(data, 'idLugar');
  const participantesIds = data.getAll('participantesIds').map(Number);
  if (!id(idLugar) || !participantesIds.length || !participantesIds.every(id)) throw new Error('Elegí un lugar y al menos un artista.');
  const target = editing;
  const result = await write(target === null ? '/api/eventos' : '/api/eventos/' + target, target === null ? 'POST' : 'PUT', {
    nombre: textValue(data, 'nombre'), descripcion: textValue(data, 'descripcion'), tipo: textValue(data, 'tipo'),
    fechaHora: textValue(data, 'fechaHora'), idLugar, participantesIds, demanda: data.has('demanda')
  });
  const eventId = result?.id;
  if (!id(eventId)) throw new Error('No se confirmó el ID del evento. Actualizá los datos antes de volver a enviar.');
  form.reset();
  if (target !== null) editDraft();
  return afterWrite(target === null ? 'Borrador creado. Configurá sus sectores e inventario.' : 'Borrador actualizado.', async () => {
    await refreshCatalog();
    if (!active()) return;
    eventSelect.value = String(eventId);
    await refreshEvent();
  });
});

onSubmit('organizer-sector', 'Creando sector…', async (data, form) => {
  const { placeId } = requireDraft();
  const result = await write('/sectores', 'POST', {
    idLugar: placeId, nombre: textValue(data, 'nombre'), tipo: textValue(data, 'tipo'),
    capacidadTotal: numericValue(data, 'capacidadTotal')
  });
  if (!id(result?.id)) throw new Error('No se confirmó el ID del sector. Actualizá los datos antes de volver a enviar.');
  form.reset();
  return afterWrite('Sector creado.', () => refreshEvent(true));
});

onSubmit('organizer-seat', 'Agregando asiento…', async (data, form) => {
  requireDraft();
  const sector = selectedSector(numericValue(data, 'idSector'));
  if (sector?.tipo !== 'NUMERADO') throw new Error('Elegí un sector numerado.');
  const result = await write('/asientos', 'POST', { idSector: sector.id, fila: textValue(data, 'fila'), columna: textValue(data, 'columna') });
  if (!id(result?.id)) throw new Error('No se confirmó el ID del asiento. Actualizá los datos antes de volver a enviar.');
  form.reset();
  numberedSelect.value = String(sector.id);
  return afterWrite('Asiento creado.', refreshSeats);
});

onSubmit('organizer-inventory', 'Guardando límite de inventario…', async data => {
  const { event } = requireDraft();
  await write('/eventos/' + event.id + '/inventario', 'PUT', { limitePorCliente: numericValue(data, 'limitePorCliente') });
  return afterWrite('Límite de inventario guardado.', () => refreshInventory(event));
});

onSubmit('organizer-pricing', 'Configurando sector del evento…', async data => {
  const { event } = requireDraft();
  const sector = selectedSector(numericValue(data, 'idSector'));
  const capacidadHabilitada = numericValue(data, 'capacidadHabilitada');
  if (!sector || capacidadHabilitada > sector.capacidadTotal) throw new Error('La capacidad habilitada debe estar dentro del cupo físico del sector.');
  await write('/eventos/' + event.id + '/sectores', 'PUT', { idSector: sector.id, precio: numericValue(data, 'precio'), capacidadHabilitada });
  return afterWrite('Sector configurado en el evento.', () => refreshInventory(event));
});

publish.addEventListener('click', () => void run('Publicando evento…', async () => {
  const { event } = requireDraft();
  if (!inventory.length) throw new Error('Configurá al menos un sector para publicar.');
  const result = await write('/api/eventos/' + event.id + '/publicar', 'PATCH') as OrganizerEvent | null;
  if (result?.id !== event.id || result.estadoPublicacion !== 'PUBLICADO') throw new Error('El servidor no confirmó la publicación. Actualizá los datos antes de reintentar.');
  events = events.map(value => value.id === result.id ? result : value);
  if (!active()) return;
  fillSelect(eventSelect, events, value => entityLabel(value) + ' · ' + value.estadoPublicacion);
  showCards();
  await refreshEvent();
  return 'Evento publicado.';
}));

function occupancy(value: number | null) {
  return value === null ? 'Sin capacidad informada' : numberFormat.format(value) + '%';
}

element('organizer-report-load').addEventListener('click', () => void run('Consultando reporte…', async () => {
  const event = selectedEvent();
  if (!event) throw new Error('Elegí un evento.');
  clearReport();
  const data = await readReport(event.id);
  if (!active()) return;
  element('organizer-report-total').textContent = 'Vendidas: ' + data.entradasVendidas + ' · Devueltas: ' + data.entradasDevueltas
    + ' · Capacidad: ' + data.capacidadTotal + ' · Ocupación: ' + occupancy(data.ocupacionPorcentaje)
    + ' · Ingresos netos: ' + numberFormat.format(data.ingresosNetos);
  const rows = element('organizer-report-rows');
  for (const sector of data.sectores) {
    const row = document.createElement('tr');
    for (const value of [sector.idSector, sector.entradasVendidas, sector.entradasDevueltas, sector.capacidad,
      occupancy(sector.ocupacionPorcentaje), numberFormat.format(sector.ingresosNetos)]) {
      const cell = document.createElement('td');
      cell.textContent = String(value);
      row.append(cell);
    }
    rows.append(row);
  }
  element('organizer-report-table').hidden = data.sectores.length === 0;
  return data.sectores.length ? 'Reporte actualizado.' : 'El reporte no contiene sectores.';
}));

eventSelect.addEventListener('change', () => void run('Consultando evento…', refreshEvent));
element('organizer-create-action').addEventListener('click', () => editDraft());
element('organizer-cancel-edit').addEventListener('click', () => {
  editDraft();
  element<HTMLDetailsElement>('organizer-draft-panel').open = false;
  element('organizer-draft-title').focus();
});
numberedSelect.addEventListener('change', () => void run('Consultando asientos…', refreshSeats));
element('organizer-refresh').addEventListener('click', () => void run('Actualizando datos…', async () => {
  await refreshCatalog();
  if (active()) await refreshEvent(true);
  return 'Datos actualizados.';
}));

function hideInactive() {
  if (active()) return;
  workspace.hidden = true;
  workspace.disabled = true;
  status.textContent = '';
}

window.addEventListener('ticketya:profile', hideInactive);
window.addEventListener('pageshow', () => { if (allowed && !active()) { hideInactive(); location.reload(); } });

if (!session) {
  location.replace('/login/?volver=' + encodeURIComponent(location.pathname + location.search + location.hash));
} else if (!allowed) {
  access.textContent = 'Esta pantalla requiere el rol ORGANIZADOR o ADMINISTRADOR.';
} else {
  access.hidden = true;
  workspace.hidden = false;
  workspace.disabled = false;
  void run('Cargando tus eventos, lugares y artistas…', async () => {
    await refreshCatalog();
    if (active()) await refreshEvent();
  });
}
