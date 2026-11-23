// Prueba integrada sin red: todas las operaciones remotas usan una API en memoria.
import { readFileSync } from 'node:fs';
import { stripTypeScriptTypes } from 'node:module';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { parseHTML } from 'linkedom';
import QRCode from 'qrcode';
import * as selection from '../../src/scripts/selection-data.ts';
import * as catalog from '../../src/scripts/catalog-data.ts';
import * as search from '../../src/scripts/catalog-search.ts';
import * as historyData from '../../src/scripts/history-data.ts';
import * as entryData from '../../src/scripts/entry-data.ts';
import * as refundData from '../../src/scripts/refund-data.ts';
import { purchaseFlow } from '../../src/scripts/purchase-attempt.ts';
import { ApiError } from '../../src/scripts/api.ts';

const memory = () => { const map = new Map(); return { getItem: key => map.get(key) ?? null, setItem: (key,value) => map.set(key,String(value)), removeItem: key => map.delete(key) }; };
const localStorage = memory(), sessionStorage = memory();
const profile = { id: 7, nombre: 'Perfil de prueba', email: 'prueba@example.com', dni: 'AB-123', ciudad: 'Tandil', idRol: 91 };
const event = { id: 700, nombre: 'Evento de prueba', descripcion: 'Solo fixture', tipo: 'RECITAL', fechaHora: '2099-12-20T21:00:00', estado: 'EN_CURSO', estadoPublicacion: 'PUBLICADO', demanda: false, lugar: { id: 4, nombre: 'Sala', ciudad: 'Tandil' }, participantes: [{ id: 71, nombre: 'Artista' }] };
const sector = { idEvento: 700, idSector: 55, tipo: 'CAMPO', precio: 1500, disponibles: 4, cancelado: false, asientos: [] };
let purchase = null, reservation = null, entries = [], refunds = [], qrPayload = null;
const calls = [];
async function api(path, options = {}) {
  const method = options.method || 'GET'; calls.push({ path, method, body: options.body });
  let data;
  if (path === '/usuarios') data = [profile];
  else if (path.startsWith('/api/eventos/buscar')) data = [event];
  else if (path === '/api/eventos/700') data = event;
  else if (path === '/eventos/700/sectores') data = [sector];
  else if (path === '/eventos/700/sectores/55/disponibilidad') data = sector;
  else if (path === '/sectores/55') data = { id: 55, nombre: 'Campo real' };
  else if (path === '/compras' && method === 'POST') {
    assert.ok(localStorage.getItem('ticketya-compra-v1-7-700'));
    assert.equal('precio' in options.body, false); assert.equal(options.body.cantidadEntradas, 1);
    purchase = { ...options.body, id: 40, idReserva: 30, cantidadEntradas: 1, idsAsientos: [], precioUnitario: 1500, importeTotal: 1500, estadoCompra: 'PENDIENTE', descripcion: 'Pendiente' };
    reservation = { id: 30, idCliente: 7, idEvento: 700, idSector: 55, estado: 'ACTIVA', venceEn: '2099-12-01T12:10:00Z' }; sector.disponibles--; data = purchase;
  } else if (path === '/compras/40/pago?aprobado=true' && method === 'PUT') {
    purchase.estadoCompra = 'APROBADA'; reservation.estado = 'CONFIRMADA';
    entries = [{ id: 50, idCliente: 7, idEvento: 700, idSector: 55, idAsiento: null, precio: 1500, estadoEntrada: 'PAGA', estadoQr: 'NO_ESCANEADO', qr: ' cadena exacta:ñ/+\n' }]; data = purchase;
  } else if (path === '/compras/40') data = purchase;
  else if (path === '/compras/cliente/7') data = purchase ? [purchase] : [];
  else if (path === '/reservas/30') data = reservation;
  else if (path === '/entradas/compra/40' || path === '/entradas/cliente/7') data = entries;
  else if (path === '/entradas/50') data = entries[0];
  else if (path === '/solicitudes/cliente/7') data = refunds;
  else if (path === '/solicitudes' && method === 'POST') {
    assert.deepEqual(JSON.parse(JSON.stringify(options.body)), { idEntrada: 50, idCliente: 7, descripcion: 'No puedo asistir' });
    data = { ...options.body, id: 60, idCompra: 40, estadoSolicitud: 'PENDIENTE', fechaSolicitud: '2030-01-01T00:00:00Z', fechaResolucion: null, estadoReintegro: null };
    refunds = [data]; entries[0].estadoEntrada = 'SOLICITUD_DEVOLUCION';
  } else throw Error('Ruta no simulada: ' + method + ' ' + path);
  return { data: structuredClone(data), status: method === 'POST' ? 201 : 200, headers: new Headers() };
}
function page(file, url) {
  const { document, window } = parseHTML(readFileSync('dist/' + file, 'utf8'));
  const templates = [...document.querySelectorAll('template')], original = document.querySelector.bind(document);
  templates.forEach(t => t.remove()); document.querySelector = selector => templates.find(t => selector === '#' + t.id) || original(selector);
  document.querySelectorAll('dialog').forEach(d => { d.showModal = function() { this.open = true; }; d.close = function() { this.open = false; this.dispatchEvent(new window.Event('close')); }; });
  document.querySelectorAll('*').forEach(el => { el.scrollIntoView = () => {}; el.scrollBy = () => {}; });
  window.matchMedia = () => ({ matches: true, addEventListener() {} });
  const location = { href: url, search: new URL(url).search, hash: new URL(url).hash, pathname: new URL(url).pathname, assign(value) { this.target = value; }, replace(value) { this.target = value; }, reload() {} };
  const context = vm.createContext({ ...selection, ...catalog, ...search, ...historyData, ...entryData, ...refundData, purchaseFlow, ApiError, api,
    document, window, location, history: { replaceState(_,__,path) { const next = new URL(path,location.href); Object.assign(location, { href: next.href, hash: next.hash, search: next.search }); } }, localStorage, sessionStorage,
    crypto: webcrypto, URL, URLSearchParams, Event: window.Event, Error, Headers,
    Option: function(label,value) { const option = document.createElement('option'); option.textContent = label; option.value = value; return option; },
    ResizeObserver: class { observe() {} }, gsap: {}, matchMedia: window.matchMedia, setTimeout: () => 1, clearTimeout() {}, setInterval: () => 1, clearInterval() {},
    QRCode: { toDataURL: async (value,options) => { qrPayload = value; return QRCode.toDataURL(value,options); } }
  });
  // session.ts exige window.location para validar las redirecciones.
  window.location = { origin: 'http://test.local' };
  const run = name => {
    const raw = readFileSync('src/scripts/' + name + '.ts','utf8');
    const exported = [...raw.matchAll(/export (?:async )?function (\w+)/g)].map(match => match[1]);
    const source = stripTypeScriptTypes(raw.replace(/^import .*;\r?\n/gm,'').replace(/^export (?:type )?\{.*;\r?\n/gm,'').replace(/^export /gm,''));
    vm.runInContext('(function(){' + source + ';Object.assign(globalThis,{' + exported.join(',') + '});})()', context, { filename: name + '.ts' });
  };
  run('session');
  return { document, window, context, location, run };
}
const settle = () => new Promise(resolve => setImmediate(resolve));
async function flush() { for (let i=0;i<8;i++) await settle(); }
const click = (p,selector) => { const node = p.document.querySelector(selector); assert.ok(node, selector); assert.ok(!node.disabled, selector + ' habilitado'); node.click(); };

let p = page('login/index.html','http://test.local/login/?volver=/');
const select = p.document.querySelector('#demo-profile'); let selected = ''; Object.defineProperty(select,'value',{get:()=>selected,set:value=>selected=String(value)}); select.add = option => select.append(option);
p.run('login'); await flush(); select.value = '7'; select.dispatchEvent(new p.window.Event('change')); p.document.querySelector('#login-form').dispatchEvent(new p.window.Event('submit',{cancelable:true}));
assert.equal(sessionStorage.getItem('ticketya-demo-profile-id'),'7'); assert.equal(p.location.target,'/');
p = page('index.html','http://test.local/'); p.run('home'); await flush(); assert.match(p.document.querySelector('#catalogo').textContent,/Evento de prueba/); click(p,'#catalogo [data-event]'); assert.equal(p.document.querySelector('#event-selection-link').getAttribute('href'),'/eventos/?id=700');
p = page('eventos/index.html','http://test.local/eventos/?id=700'); p.run('cart'); p.run('selection-api'); p.run('purchase-ui'); p.run('event'); await flush();
const qty=p.document.querySelector('#quantity'); qty.value='1'; qty.dispatchEvent(new p.window.Event('input')); click(p,'#review-selection'); click(p,'#save-cart'); await flush(); assert.ok(localStorage.getItem('ticketya-carrito-v2-perfil-7'));
p = page('perfil/index.html','http://test.local/perfil/#carrito'); p.run('cart'); p.run('selection-api'); p.run('cart-view'); p.context.initCart(); await flush(); assert.match(p.document.querySelector('#cart-list').textContent,/Coincide con la disponibilidad/); const href=p.document.querySelector('#cart-list a').getAttribute('href');
p = page('eventos/index.html','http://test.local'+href); p.run('cart'); p.run('selection-api'); p.run('purchase-ui'); p.run('event'); await flush(); assert.match(p.document.querySelector('#summary-items').textContent,/1 entradas/); click(p,'#review-selection'); click(p,'#create-purchase'); await flush(); assert.match(p.document.querySelector('#reservation-status').textContent,/ACTIVA/); click(p,'#approve-payment'); await flush(); assert.match(p.document.querySelector('#payment-state').textContent,/APROBADA/); assert.equal(calls.filter(c=>c.method==='POST'&&c.path==='/compras').length,1);
p = page('perfil/index.html','http://test.local/perfil/#compras'); p.run('history'); p.run('refunds'); p.run('tickets'); p.context.initHistory(7); p.context.initTickets(); await flush(); assert.match(p.document.querySelector('#purchase-list').textContent,/APROBADA/); click(p,'#purchase-list [data-purchase-tickets]'); await flush(); assert.ok(calls.some(c=>c.path==='/entradas/compra/40')); click(p,'#ticket-list .entry-qr');
for(let i=0;i<20 && p.document.querySelector('#ticket-qr-image').hidden;i++) await new Promise(resolve=>setTimeout(resolve,10));
assert.equal(qrPayload,entries[0].qr); assert.match(p.document.querySelector('#ticket-qr-image').src,/^data:image\/png;base64,/); p.document.querySelector('#ticket-qr-dialog').close();
click(p,'#ticket-list .entry-refund'); await flush(); p.document.querySelector('#refund-description').value='No puedo asistir'; p.document.querySelector('#refund-form').dispatchEvent(new p.window.Event('submit',{cancelable:true})); await flush();
assert.match(p.document.querySelector('#refund-list').textContent,/PENDIENTE/); assert.match(p.document.querySelector('#ticket-list').textContent,/SOLICITUD_DEVOLUCION/); assert.equal(p.document.querySelector('#ticket-list .entry-qr').disabled,true);
assert.equal(calls.filter(c=>c.method==='POST'&&c.path==='/solicitudes').length,1); assert.equal(calls.some(c=>c.path.includes('/uso')),false); assert.equal(calls.filter(c=>c.path==='/reservas'&&c.method==='POST').length,0);
console.log('Recorrido integrado aprobado: perfil → catálogo → detalle → carrito revalidado → compra → reserva → pago simulado → historial → entradas por compra → QR exacto → devolución pendiente.');
console.log('API íntegramente en memoria: ninguna llamada de red ni escritura al backend.');
