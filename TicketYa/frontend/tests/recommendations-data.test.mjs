import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseRecommendations, recommendationWarnings } from '../src/scripts/recommendations-data.ts';
const response = { recomendaciones: [{ idEvento: 700, nombre: 'Evento real', tipo: 'RECITAL', fechaHora: '2030-12-20T21:00:00', ciudad: 'Tandil', puntuacion: 3, motivos: ['Coincide con tus preferencias'], fuentes: ['PREFERENCIAS'] }], parcial: false, fuentesNoDisponibles: [], historialVerificado: true, disponibilidadVerificada: true };
test('conserva motivos, fuentes y orden del servidor sin inventar cupo ni precio', () => {
  const parsed = parseRecommendations(response);
  assert.deepEqual(parsed, response);
  assert.equal('disponibles' in parsed.recomendaciones[0], false);
  assert.equal('precio' in parsed.recomendaciones[0], false);
});
test('advierte respuesta parcial, fuentes faltantes y ambas verificaciones fallidas', () => {
  const warnings = recommendationWarnings({ ...response, parcial: true, fuentesNoDisponibles: ['COMPRAS'], historialVerificado: false, disponibilidadVerificada: false });
  assert.equal(warnings.length, 4);
  assert.match(warnings.join(' '), /COMPRAS/);
  assert.match(warnings.join(' '), /ya compraste/);
  assert.match(warnings.join(' '), /comprobar el cupo/);
});
test('vacío es válido y no implica error; fuentes faltantes no se ocultan aunque parcial sea false', () => {
  assert.deepEqual(parseRecommendations({ ...response, recomendaciones: [] }).recomendaciones, []);
  assert.deepEqual(recommendationWarnings(response), []);
  assert.equal(recommendationWarnings({ ...response, fuentesNoDisponibles: ['PREFERENCIAS'] }).length, 1);
});
test('la ciudad puede llegar como null sin invalidar la recomendación', () => {
  const payload = { ...response, recomendaciones: [{ ...response.recomendaciones[0], ciudad: null }] };
  assert.equal(parseRecommendations(payload).recomendaciones[0].ciudad, null);
});
test('no interpreta flags ausentes o cadenas como verificación exitosa', () => {
  for (const value of [null, { ...response, historialVerificado: undefined }, { ...response, disponibilidadVerificada: 'false' }, { ...response, fuentesNoDisponibles: null }, { ...response, recomendaciones: [response.recomendaciones[0], response.recomendaciones[0]] }]) assert.throws(() => parseRecommendations(value));
});
