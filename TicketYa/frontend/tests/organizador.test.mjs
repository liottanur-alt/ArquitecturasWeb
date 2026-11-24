import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import vm from 'node:vm';
import { ApiError } from '../src/scripts/api.ts';

const source = stripTypeScriptTypes(readFileSync(new URL('../src/scripts/organizador.ts', import.meta.url), 'utf8')
  .replace(/^import .*;\r?\n/gm, ''));
const flush = () => new Promise(resolve => setImmediate(resolve));

function screen(session, responses = {}) {
  const nodes = new Map(), calls = [];
  function node(name) {
    if (!nodes.has(name)) nodes.set(name, {
      textContent: '', hidden: true, disabled: name === 'organizer-workspace',
      value: '', children: [], fields: {}, handlers: new Map(),
      multiple: name === 'organizer-artists-select',
      get options() { return this.children; },
      get selectedOptions() { return this.children.filter(option => this.multiple ? option.selected : option.value === this.value); },
      replaceChildren(...children) {
        this.children = children;
        this.value = children.find(option => option.selected)?.value ?? children[0]?.value ?? '';
      },
      append(child) { this.children.push(child); },
      reset() { this.fields = {}; },
      addEventListener(type, handler) { this.handlers.set(type, handler); },
    });
    return nodes.get(name);
  }
  const location = {
    pathname: '/organizador/', search: '', hash: '',
    replace(value) { this.target = value; }
  };
  vm.runInNewContext(source, {
    ApiError, getSession: () => session, window: new EventTarget(), location,
    document: { getElementById: node, createElement: () => node(Symbol()) },
    Option: function (label, value, _, selected = false) { Object.assign(this, { label, value, selected }); },
    FormData: function (form) {
      this.get = key => form.fields[key] ?? null;
      this.getAll = key => Array.isArray(form.fields[key]) ? form.fields[key] : [];
      this.has = key => Object.hasOwn(form.fields, key);
    },
    api: async (path, options = {}) => {
      const route = (options.method || 'GET') + ' ' + path;
      calls.push({ route, body: options.body && JSON.parse(JSON.stringify(options.body)) });
      if (!Object.hasOwn(responses, route)) throw new Error('Ruta inesperada ' + route);
      const result = responses[route];
      return { data: typeof result === 'function' ? result(options.body) : result };
    },
  });
  const trigger = async (name, type = 'click', fields = {}) => {
    node(name).fields = fields;
    node(name).handlers.get(type)({ preventDefault() {} });
    await flush();
  };
  return { node, calls, trigger, location };
}

test('pide sesión o informa acceso insuficiente antes de consultar datos operativos', () => {
  const guest = screen(null);
  assert.equal(guest.location.target, '/login/?volver=%2Forganizador%2F');
  assert.equal(guest.calls.length, 0);
  const client = screen({ userId: 7, roles: ['CLIENTE'] });
  assert.match(client.node('organizer-access').textContent, /ORGANIZADOR/);
  assert.equal(client.node('organizer-workspace').hidden, true);
  assert.equal(client.calls.length, 0);
});

test('crea y publica un evento numerado y consulta el reporte del Gateway', async () => {
  const place = { id: 11, nombre: 'Sala' };
  const artist = { id: 15, nombre: 'Banda' };
  const sector = { id: 36, nombre: 'Balcón', tipo: 'NUMERADO', capacidadTotal: 1, idLugar: place.id };
  let places = [], artists = [], events = [], sectors = [], seats = [], inventory = null;
  const sales = { entradasVendidas: 1, entradasDevueltas: 0, ingresosNetos: 1550, ocupacionPorcentaje: 100 };
  const { trigger, node, calls } = screen({ userId: 7, accessToken: 'simulado', roles: ['ORGANIZADOR'] }, {
    'GET /api/lugares': () => places,
    'POST /api/lugares': () => { places = [place]; return place; },
    'GET /api/artistas': () => artists,
    'POST /api/artistas': () => { artists = [artist]; return artist; },
    'GET /api/eventos/mis-eventos': () => events,
    'POST /api/eventos': body => {
      const event = { id: 22, nombre: body.nombre, lugar: place, fechaHora: body.fechaHora, estadoPublicacion: 'BORRADOR', idOrganizador: 7 };
      events = [event];
      return event;
    },
    'GET /sectores?idLugar=11': () => sectors,
    'POST /sectores': () => { sectors = [sector]; return sector; },
    'GET /asientos?idSector=36': () => seats,
    'POST /asientos': body => { const seat = { id: 40, ...body }; seats = [seat]; return seat; },
    'PUT /eventos/22/inventario': () => { inventory = []; return null; },
    'PUT /eventos/22/sectores': body => {
      inventory = [{ idSector: 36, capacidad: body.capacidadHabilitada, precio: body.precio }];
      return inventory[0];
    },
    'GET /eventos/22/sectores': () => {
      if (inventory === null) throw new ApiError(new Response(null, { status: 404 }), null);
      return inventory;
    },
    'PATCH /api/eventos/22/publicar': () => {
      events = [{ ...events[0], estadoPublicacion: 'PUBLICADO' }];
      return events[0];
    },
    'GET /api/reportes/eventos/22': { idEvento: 22, capacidadTotal: 1, ...sales, sectores: [{ idSector: 36, capacidad: 1, ...sales }] },
  });

  await flush();
  await trigger('organizer-place', 'submit', { nombre: 'Sala', direccion: 'Calle 10', ciudad: 'Tandil', tipo: 'SALA', latitud: '-37', longitud: '-59' });
  await trigger('organizer-artist', 'submit', { nombre: 'Banda', genero: 'Rock' });
  assert.equal(node('organizer-place-select').value, '11');
  assert.equal(node('organizer-artists-select').selectedOptions[0].value, '15');
  await trigger('organizer-draft', 'submit', {
    nombre: 'Recital', descripcion: 'Música en vivo', tipo: 'RECITAL', idLugar: '11',
    fechaHora: '2030-06-01T20:00', participantesIds: ['15'], demanda: 'on'
  });
  assert.match(node('organizer-inventory-summary').textContent, /todavía no/);
  assert.equal(node('organizer-publish').disabled, true);
  await trigger('organizer-sector', 'submit', { nombre: 'Balcón', capacidadTotal: '1', tipo: 'NUMERADO' });
  await trigger('organizer-seat', 'submit', { idSector: '36', fila: 'A', columna: '1' });
  assert.equal(node('organizer-seats').textContent, 'Asientos cargados: 1');
  assert.equal(node('organizer-seat-map').children[0].children[1].textContent, '1');
  await trigger('organizer-inventory', 'submit', { limitePorCliente: '1' });
  await trigger('organizer-pricing', 'submit', { idSector: '36', precio: '1550', capacidadHabilitada: '1' });
  assert.equal(node('organizer-publish').disabled, false);
  await trigger('organizer-publish');
  assert.equal(node('organizer-draft-actions').hidden, true);
  assert.match(node('organizer-status').textContent, /Evento publicado/);
  await trigger('organizer-report-load');
  assert.match(node('organizer-report-total').textContent, /Vendidas: 1/);
  assert.equal(node('organizer-report-rows').children.length, 1);
  assert.deepEqual(calls.find(call => call.route === 'POST /api/eventos').body, {
    nombre: 'Recital', descripcion: 'Música en vivo', tipo: 'RECITAL', fechaHora: '2030-06-01T20:00',
    idLugar: 11, participantesIds: [15], demanda: true
  });
  assert.equal(calls.some(call => call.route.includes('/interno/')), false);
});
