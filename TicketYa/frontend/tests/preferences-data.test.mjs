import { test } from 'node:test';
import assert from 'node:assert/strict';
import { preferencesBody, parsePreferences, parseArtists } from '../src/scripts/preferences-data.ts';
test('guardar preferencias envía ambos campos completos, normalizados y sin favoritos', () => {
  assert.deepEqual(preferencesBody(['RECITAL','FESTIVAL','RECITAL'], ' Tandil '), { tiposEventoPreferidos: ['RECITAL','FESTIVAL'], ciudadPreferida: 'Tandil' });
  assert.deepEqual(preferencesBody([], '  '), { tiposEventoPreferidos: [], ciudadPreferida: null });
  assert.throws(() => preferencesBody(['Música'], 'Tandil'));
  assert.throws(() => preferencesBody([], 'x'.repeat(101)));
});
test('preferencias no derivan ciudad del perfil y preservan IDs favoritos no resueltos', () => {
  assert.deepEqual(parsePreferences({ tiposEventoPreferidos: [], ciudadPreferida: null, artistasFavoritos: [90, 90] }), { tiposEventoPreferidos: [], ciudadPreferida: null, artistasFavoritos: [90] });
  assert.throws(() => parsePreferences({ tiposEventoPreferidos: [], ciudadPreferida: 'Tandil', artistasFavoritos: ['90'] }));
});
test('catálogo usa id; favoritos del backend usan idArtista', () => {
  assert.deepEqual(parseArtists([{ id: 71, nombre: 'Artista', genero: 'Rock' }]), [{ id: 71, nombre: 'Artista', genero: 'Rock' }]);
  assert.deepEqual(parseArtists([{ idArtista: 71, nombre: 'Artista', genero: 'Rock' }], true), [{ id: 71, nombre: 'Artista', genero: 'Rock' }]);
  assert.throws(() => parseArtists([{ idArtista: 71, nombre: 'Artista' }]));
});
test('listas vacías se distinguen de respuestas inválidas y no inventan artistas', () => {
  assert.deepEqual(parseArtists([]), []);
  for (const value of [null, [{ id: 1, nombre: '' }], [{ id: 1, nombre: 'A' }, { id: 1, nombre: 'B' }]]) assert.throws(() => parseArtists(value));
});
