import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mapCatalog, catalogDate, setEventImage } from '../src/scripts/catalog-data.ts';

const event = { id: 702, nombre: 'Festival real', descripcion: 'Descripción', tipo: 'FESTIVAL', fechaHora: '2030-12-20T21:30:00',
  participantes: [{ id: 81, nombre: 'Artista' }], lugar: { nombre: 'Sala', ciudad: 'Tandil', direccion: 'Calle 1' }, estado: 'AGOTADO', demanda: true };
test('mapea el contrato real sin precios, ventas o disponibilidad inventados', () => {
  const [mapped] = mapCatalog([event]);
  assert.equal(mapped.title, event.nombre);
  assert.equal(mapped.category, 'Festivales');
  assert.equal(mapped.artist, 'Artista');
  assert.equal(mapped.location, 'Sala · Tandil');
  assert.equal(mapped.state, 'Agotado');
  assert.equal(mapped.high, true);
  assert.equal(mapped.image, '/event-placeholder.svg');
  for (const field of ['price', 'sold', 'available', 'sectors', 'disponibles']) assert.equal(field in mapped, false);
});
test('fecha local conserva día, año y hora sin depender de la zona del equipo', () => {
  for (const value of ['2031-01-01T00:30', '2031-01-01T00:30:00', '2031-01-01T00:30:00.123']) {
    const date = catalogDate(value);
    assert.equal(date.day, '01');
    assert.match(date.monthYear, /2031/);
    assert.match(date.fullDate, /00:30/);
  }
  for (const value of [null, '', '2030-02-31T12:00:00', '2030-01-01T25:00:00', '2030-01-01T12:00:00Z']) {
    assert.equal(catalogDate(value).fullDate, 'Fecha no informada');
  }
});
test('usa la imagen opcional de la API y recupera la ilustración si falla', () => {
  for (const url of ['/eventos-destacados/recital.png', 'https://example.com/teatro.webp']) {
    assert.equal(mapCatalog([{ ...event, imagenUrl: url }])[0].image, url);
  }
  for (const url of [null, 'javascript:alert(1)', '//example.com/imagen.png', '/\\example.com/imagen.png']) {
    assert.equal(mapCatalog([{ ...event, imagenUrl: url }])[0].image, '/event-placeholder.svg');
  }
  const image = {};
  setEventImage(image, '/eventos-destacados/recital.png');
  assert.equal(image.src, '/eventos-destacados/recital.png');
  image.onerror();
  assert.equal(image.src, '/event-placeholder.svg');
  assert.equal(image.onerror, null);
});
test('distingue vacío, respuesta inválida y campos sin informar', () => {
  assert.deepEqual(mapCatalog([]), []);
  assert.throws(() => mapCatalog(null));
  assert.throws(() => mapCatalog([{ id: 0 }]));
  assert.throws(() => mapCatalog([event, event]));
  const [mapped] = mapCatalog([{ id: 1 }]);
  assert.equal(mapped.title, 'Evento sin nombre');
  assert.equal(mapped.city, '');
  assert.equal(mapped.fullDate, 'Fecha no informada');
  assert.equal(mapped.high, false);
});
