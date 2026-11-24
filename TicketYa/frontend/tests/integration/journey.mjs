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
import { parseRecommendations } from '../../src/scripts/recommendations-data.ts';
import * as historyData from '../../src/scripts/history-data.ts';
import * as entryData from '../../src/scripts/entry-data.ts';
import * as refundData from '../../src/scripts/refund-data.ts';
import { purchaseFlow } from '../../src/scripts/purchase-attempt.ts';
import { ApiError } from '../../src/scripts/api.ts';

const memory = () => { const map = new Map(); return { getItem: key => map.get(key) ?? null, setItem: (key,value) => map.set(key,String(value)), removeItem: key => map.delete(key) }; };
const localStorage = memory(), sessionStorage = memory();
const profile = { id: 7, nombre: 'Perfil de prueba', email: 'prueba@example.com', dni: 'AB-123', ciudad: 'Tandil', idRol: 91 };
const token = `eyJhbGciOiJIUzI1NiJ9.${Buffer.from(JSON.stringify({ sub: '7', exp: Math.floor(Date.now() / 1000) + 1800 })).toString('base64url')}.fixture`;
const event = { id: 700, nombre: 'Evento de prueba', descripcion: 'Solo fixture', tipo: 'RECITAL', fechaHora: '2099-12-20T21:00:00', estado: 'EN_CURSO', estadoPublicacion: 'PUBLICADO', demanda: false, lugar: { id: 4, nombre: 'Sala', ciudad: 'Tandil' }, participantes: [{ id: 71, nombre: 'Artista' }] };
const sector = { idEvento: 700, idSector: 55, tipo: 'CAMPO', precio: 1500, disponibles: 4, cancelado: false, asientos: [] };
let purchase = null, entries = [], refunds = [], qrPayload = null;
let turn = null, losePurchaseResponse = false;
const purchaseKey = 'ticketya-compra-v1-7-700';
const calls = [];
async function api(path, options = {}) {
  const method = options.method || 'GET'; calls.push({ path, method, body: options.body });
  let data;
  if (path === '/auth/login' && method === 'POST') {
    assert.equal(options.body.email, profile.email);
    assert.equal(options.body.password, 'fixture');
    data = { accessToken: token, tokenType: 'Bearer', expiresIn: 1800 };
  }
  else if (path.startsWith('/api/eventos/buscar')) data = [event];
  else if (path === '/api/eventos/700') data = event;
  else if (path === '/usuarios/7/recomendaciones') data = {
    recomendaciones: [{ idEvento: event.id, nombre: event.nombre, tipo: event.tipo, fechaHora: event.fechaHora,
      ciudad: event.lugar.ciudad, puntuacion: 3, motivos: ['Coincide con un tipo de evento preferido'], fuentes: ['PREFERENCIAS'] }],
    parcial: false, fuentesNoDisponibles: [], historialVerificado: true, disponibilidadVerificada: true,
  };
  else if (path === '/eventos/700/sectores') data = [sector];
  else if (path === '/eventos/700/sectores/55/disponibilidad') data = sector;
  else if (path === '/sectores/55') data = { id: 55, nombre: 'Campo real' };
  else if ((path === '/api/eventos/700/fila' && method === 'POST')
    || (path === '/api/eventos/700/fila/mi-turno' && method === 'GET')) {
    assert.equal(event.demanda, true);
    assert.equal(options.body, undefined);
    if (!turn) throw new ApiError(new Response(null, { status: 404 }), null);
    data = { eventoId: 700, turnoId: 'turno-fixture', posicion: null, autorizacionCompra: null, venceEn: null, ...turn };
  }
  else if (path === '/compras' && method === 'POST') {
    assert.deepEqual(JSON.parse(localStorage.getItem(purchaseKey)).body, JSON.parse(JSON.stringify(options.body)));
    assert.equal('precio' in options.body, false);
    if (sector.tipo === 'NUMERADO') {
      assert.deepEqual(options.body.idsAsientos, [901]);
      assert.equal(options.body.cantidadEntradas, undefined);
    } else {
      assert.equal(options.body.cantidadEntradas, 1);
    }
    assert.equal(options.body.autorizacionCompra, event.demanda ? 'autorizacion-fixture' : undefined);
    if (!purchase) {
      if (event.demanda) assert.equal(turn?.estado, 'ADMITIDO');
      const { autorizacionCompra, ...pedido } = options.body;
      purchase = { ...pedido, id: 40, idReserva: 30, cantidadEntradas: 1, idsAsientos: pedido.idsAsientos ?? [], precioUnitario: 1500, importeTotal: 1500, estadoCompra: 'PENDIENTE', descripcion: 'Pendiente' };
      sector.disponibles--;
    } else {
      assert.equal(options.body.claveIdempotencia, purchase.claveIdempotencia);
    }
    if (losePurchaseResponse) {
      losePurchaseResponse = false;
      throw new Error('Se perdió la respuesta de compra');
    }
    data = purchase;
  } else if (path === '/compras/40/pago?aprobado=true' && method === 'PUT') {
    purchase.estadoCompra = 'APROBADA';
    entries = [{ id: 50, idCliente: 7, idEvento: 700, idSector: 55, idAsiento: null, precio: 1500, estadoEntrada: 'PAGA', estadoQr: 'NO_ESCANEADO', qr: ' cadena exacta:ñ/+\n' }]; data = purchase;
  } else if (path === '/compras/40') data = purchase;
  else if (path === '/compras/cliente/7') data = purchase ? [purchase] : [];
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
  const timers = new Map();
  let nextTimer = 0;
  const schedule = (callback, repeating) => {
    const id = ++nextTimer;
    timers.set(id, { callback, repeating });
    return id;
  };
  const cancel = id => timers.delete(id);
  const tick = () => {
    for (const [id, { callback, repeating }] of [...timers]) {
      if (!timers.has(id)) continue;
      if (!repeating) timers.delete(id);
      callback();
    }
  };
  const context = vm.createContext({ ...selection, ...catalog, ...search, ...historyData, ...entryData, ...refundData, parseRecommendations, purchaseFlow, ApiError, api,
    document, window, location, history: { replaceState(_,__,path) { const next = new URL(path,location.href); Object.assign(location, { href: next.href, hash: next.hash, search: next.search }); } }, localStorage, sessionStorage,
    crypto: webcrypto, URL, URLSearchParams, Event: window.Event, Error, Headers, atob,
    Option: function(label,value) { const option = document.createElement('option'); option.textContent = label; option.value = value; return option; },
    ResizeObserver: class { observe() {} }, gsap: {}, matchMedia: window.matchMedia, AbortController, AbortSignal,
    setTimeout: callback => schedule(callback, false), clearTimeout: cancel,
    setInterval: callback => schedule(callback, true), clearInterval: cancel,
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
  if (file === 'eventos/index.html') run('purchase-queue');
  return { document, window, context, location, run, tick };
}
const settle = () => new Promise(resolve => setImmediate(resolve));
async function flush() { for (let i=0;i<8;i++) await settle(); }
const click = (p,selector) => { const node = p.document.querySelector(selector); assert.ok(node, selector); assert.ok(!node.disabled, selector + ' habilitado'); node.click(); };

let p = page('login/index.html','http://test.local/login/?volver=/');
const loginForm = p.document.querySelector('#login-form');
loginForm.reportValidity = () => true;
p.run('login');
p.document.querySelector('#login-email').value = profile.email;
p.document.querySelector('#login-password').value = 'fixture';
loginForm.dispatchEvent(new p.window.Event('submit',{cancelable:true})); await flush();
assert.equal(p.context.getAuthenticatedUserId(), 7); assert.equal(p.location.target,'/');
p = page('index.html','http://test.local/'); p.run('home'); await flush();
assert.match(p.document.querySelector('#catalogo').textContent, /Evento de prueba/);
assert.match(p.document.querySelector('#catalogo [data-price]').textContent, /1.500/);
assert.equal(p.document.querySelector('#catalogo .home-card-action').getAttribute('href'), '/eventos/?id=700');
assert.match(p.document.querySelector('#para-vos [data-reason]').textContent, /tipo de evento preferido/);
assert.equal(p.document.querySelector('#para-vos .home-card-action').getAttribute('href'), '/eventos/?id=700');
p = page('eventos/index.html','http://test.local/eventos/?id=700'); p.run('cart'); p.run('selection-api'); p.run('purchase-ui'); p.run('event'); await flush();
const qty = p.document.querySelector('#quantity');
const subtract = p.document.querySelector('#subtract-quantity');
assert.equal(subtract.disabled, true);
assert.match(p.document.querySelector('#sector-list').textContent, /Campo real/);
assert.equal(p.document.querySelector('#mobile-review-selection').hidden, true);
click(p, '#add-quantity');
click(p, '#add-quantity');
assert.equal(qty.value, '2');
click(p, '#subtract-quantity');
assert.equal(qty.value, '1');
assert.equal(p.document.querySelector('#purchase-panel').hidden, true);
sector.precio = 1750;
click(p, '#refresh-availability');
await flush();
assert.equal(qty.value, '1');
assert.match(p.document.querySelector('#sector-update').textContent, /1.750/);
assert.match(p.document.querySelector('#mobile-from-price').textContent, /1 entrada.+1.750/);
assert.equal(p.document.querySelector('#jump-to-sectors').hidden, true);
click(p, '#mobile-review-selection');
assert.match(p.document.querySelector('#review-items').textContent, /1 entrada/);
assert.equal(p.document.querySelector('#review-total').textContent, p.document.querySelector('#total').textContent);
click(p, '#save-cart');
await flush();
assert.ok(localStorage.getItem('ticketya-carrito-v2-perfil-7'));
sector.precio = 1500;
p = page('perfil/index.html','http://test.local/perfil/#carrito'); p.run('cart'); p.run('selection-api'); p.run('cart-view'); p.context.initCart(); await flush(); assert.match(p.document.querySelector('#cart-list').textContent,/Coincide con la disponibilidad/); const href=p.document.querySelector('#cart-list a').getAttribute('href');
p = page('eventos/index.html', 'http://test.local' + href);
p.run('cart'); p.run('selection-api'); p.run('purchase-ui'); p.run('event');
await flush();
assert.match(p.document.querySelector('#summary-items').textContent, /1 entrada\b/);
click(p, '#review-selection');
click(p, '#create-purchase');
await flush();
assert.equal(p.document.querySelector('#payment-state').dataset.state, 'PENDIENTE');
assert.equal(p.document.querySelector('#payment-controls').hidden, false);

p = page('perfil/index.html', 'http://test.local/perfil/#compras');
p.run('history');
p.context.initHistory(7);
await flush();
const resumeLink = p.document.querySelector('#purchase-list a[href*="compra=40"]');
assert.ok(resumeLink, 'El historial permite retomar una compra pendiente');

const beforeResume = calls.length;
p = page('eventos/index.html', 'http://test.local' + resumeLink.getAttribute('href'));
p.run('cart'); p.run('selection-api'); p.run('purchase-ui'); p.run('event');
await flush();
assert.ok(calls.slice(beforeResume).some(c => c.method === 'GET' && c.path === '/compras/40'));
click(p, '#approve-payment');
await flush();
assert.equal(p.document.querySelector('#payment-state').dataset.state, 'APROBADA');
assert.equal(p.document.querySelector('#payment-controls').hidden, true);
assert.equal(calls.filter(c => c.method === 'POST' && c.path === '/compras').length, 1);
p = page('perfil/index.html','http://test.local/perfil/#compras'); p.run('history'); p.run('refunds'); p.run('tickets'); p.context.initHistory(7); p.context.initTickets(); await flush(); assert.match(p.document.querySelector('#purchase-list').textContent,/APROBADA/); click(p,'#purchase-list [data-purchase-tickets]'); await flush(); assert.ok(calls.some(c=>c.path==='/entradas/compra/40')); click(p,'#ticket-list .entry-qr');
for(let i=0;i<20 && p.document.querySelector('#ticket-qr-image').hidden;i++) await new Promise(resolve=>setTimeout(resolve,10));
assert.equal(qrPayload,entries[0].qr); assert.match(p.document.querySelector('#ticket-qr-image').src,/^data:image\/png;base64,/); p.document.querySelector('#ticket-qr-dialog').close();
click(p,'#ticket-list .entry-refund'); await flush(); p.document.querySelector('#refund-description').value='No puedo asistir'; p.document.querySelector('#refund-form').dispatchEvent(new p.window.Event('submit',{cancelable:true})); await flush();
assert.match(p.document.querySelector('#refund-list').textContent,/PENDIENTE/); assert.ok(p.document.querySelector('#ticket-list [data-state="SOLICITUD_DEVOLUCION"]')); assert.equal(p.document.querySelector('#ticket-list .entry-qr').disabled,true);
assert.equal(calls.filter(c=>c.method==='POST'&&c.path==='/solicitudes').length,1); assert.equal(calls.some(c=>c.path.includes('/uso')),false);
assert.equal(calls.some(c => /^\/reservas(?:\/|$)/.test(c.path)), false);
assert.equal(calls.some(c => c.path.includes('/fila')), false);
console.log('Recorrido integrado aprobado: login → catálogo → detalle → carrito revalidado → compra → recuperación → pago simulado → historial → entradas por compra → QR exacto → devolución pendiente.');

event.demanda = true;
Object.assign(sector, { tipo: 'NUMERADO', disponibles: 2, asientos: [
  { idAsiento: 901, fila: 'A', columna: '1', estado: 'DISPONIBLE' },
  { idAsiento: 902, fila: 'A', columna: '2', estado: 'DISPONIBLE' },
] });
turn = { estado: 'EN_ESPERA', posicion: 4 };
purchase = null;
localStorage.removeItem(purchaseKey);
const queueStart = calls.length;
p = page('eventos/index.html', 'http://test.local/eventos/?id=700');
p.run('cart'); p.run('selection-api'); p.run('purchase-ui'); p.run('event');
await flush();
click(p, '[data-seat="901"]');
click(p, '[data-seat="902"]');
sector.asientos[1].estado = 'VENDIDO';
sector.disponibles = 1;
click(p, '#refresh-availability');
await flush();
assert.equal(p.document.querySelector('[data-seat="901"]').getAttribute('aria-pressed'), 'true');
assert.equal(p.document.querySelector('[data-seat="902"]').hasAttribute('data-stock-changed'), true);
assert.match(p.document.querySelector('#sector-update').textContent, /fila A, asiento 2/);
click(p, '#review-selection');
assert.equal(p.document.querySelector('#create-purchase').disabled, true);
click(p, '#join-queue');
await flush();
assert.match(p.document.querySelector('#queue-status').textContent, /4/);
assert.equal(p.document.querySelector('#create-purchase').disabled, true);

turn = { estado: 'VENCIDO', venceEn: '2000-01-01T00:00:00Z' };
p.tick();
await flush();
assert.equal(p.document.querySelector('#create-purchase').disabled, true);
assert.equal(calls.slice(queueStart).some(c => c.path === '/compras'), false);

turn = { estado: 'ADMITIDO', autorizacionCompra: 'autorizacion-fixture', venceEn: new Date(Date.now() + 120_000).toISOString() };
click(p, '#join-queue');
await flush();
losePurchaseResponse = true;
click(p, '#create-purchase');
await flush();
assert.equal(JSON.parse(localStorage.getItem(purchaseKey)).body.autorizacionCompra, 'autorizacion-fixture');
assert.equal(JSON.parse(localStorage.getItem(purchaseKey)).purchaseId, undefined);
turn = { estado: 'VENCIDO', venceEn: '2000-01-01T00:00:00Z' };
click(p, '#recover-purchase');
await flush();
const queueCalls = calls.slice(queueStart);
const attempts = queueCalls.filter(c => c.method === 'POST' && c.path === '/compras');
assert.equal(attempts.length, 2);
assert.deepEqual(attempts[0].body, attempts[1].body);
assert.equal(queueCalls.filter(c => c.method === 'POST' && c.path === '/api/eventos/700/fila').length, 2);
assert.ok(queueCalls.some(c => c.method === 'GET' && c.path.endsWith('/fila/mi-turno')));
assert.equal(p.document.querySelector('#payment-state').dataset.state, 'PENDIENTE');

localStorage.removeItem(purchaseKey);
turn = { estado: 'EN_ESPERA', posicion: 2 };
sector.disponibles = 1;
p = page('eventos/index.html', 'http://test.local/eventos/?id=700');
p.run('cart'); p.run('selection-api'); p.run('purchase-ui'); p.run('event');
await flush();
click(p, '[data-seat="901"]');
click(p, '#review-selection');
click(p, '#join-queue');
await flush();
assert.match(p.document.querySelector('#queue-status').textContent, /2/);
click(p, '#close-review');
const afterClose = calls.length;
p.tick();
await flush();
assert.equal(calls.slice(afterClose).some(c => c.path.includes('/fila')), false);
console.log('Fila virtual aprobada: espera → turno vencido → nuevo turno admitido → compra → recuperación con la misma autorización y clave. Cerrar el diálogo detiene la consulta.');
console.log('API íntegramente en memoria: ninguna llamada de red ni escritura al backend.');
