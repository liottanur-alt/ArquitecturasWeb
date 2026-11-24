import { api } from './api';
import { getAuthenticatedUserId } from './session';
import { preferredTypes, preferencesBody, parsePreferences, parseArtists } from './preferences-data';
export function initPreferences(owner: number) {
  const form = document.querySelector<HTMLFormElement>('#preferences-form')!;
  const fields = document.querySelector<HTMLFieldSetElement>('#preferences-fields')!;
  const city = document.querySelector<HTMLInputElement>('#preferred-city')!;
  const status = document.querySelector<HTMLElement>('#preferences-status')!;
  const artistStatus = document.querySelector<HTMLElement>('#artists-status')!;
  const reload = document.querySelector<HTMLButtonElement>('#reload-preferences')!;
  const artistReload = document.querySelector<HTMLButtonElement>('#reload-artists')!;
  const choices = document.querySelector<HTMLSelectElement>('#artist-choice')!;
  const add = document.querySelector<HTMLButtonElement>('#add-artist')!;
  const list = document.querySelector<HTMLElement>('#favorite-artists')!;
  let ids: number[] = [], artists: ReturnType<typeof parseArtists> = [], resolved: ReturnType<typeof parseArtists> = [];
  let ready = false, prefsBusy = false, artistsBusy = false;
  const active = () => getAuthenticatedUserId() === owner;
  const get = async (path: string) => (await api(path, { cache: 'no-store' })).data;
  const prefix = '/usuarios/' + owner;
  const types = document.querySelector<HTMLElement>('#preferred-types')!;
  for (const type of preferredTypes) { const label = document.createElement('label'); const box = document.createElement('input'); box.type = 'checkbox'; box.value = type; box.name = 'preferred-type'; label.append(box, document.createTextNode(type)); types.append(label); }
  function apply(values: { tiposEventoPreferidos: string[]; ciudadPreferida: string | null }) {
    city.value = values.ciudadPreferida || '';
    types.querySelectorAll<HTMLInputElement>('input').forEach(box => box.checked = values.tiposEventoPreferidos.includes(box.value));
  }
  function paintArtists() {
    if (!active()) return;
    const selected = choices.value;
    choices.replaceChildren(new Option('Elegí un artista del catálogo', ''));
    for (const artist of artists.filter(a => !ids.includes(a.id))) choices.add(new Option(artist.nombre + ' · #' + artist.id + (artist.genero ? ' · ' + artist.genero : ''), String(artist.id)));
    if (artists.some(a => String(a.id) === selected && !ids.includes(a.id))) choices.value = selected;
    choices.disabled = prefsBusy || artistsBusy || !ready || !artists.length;
    add.disabled = prefsBusy || artistsBusy || !ready || !choices.value;
    list.replaceChildren();
    for (const id of ids) {
      const artist = resolved.find(a => a.id === id) || artists.find(a => a.id === id);
      const item = document.createElement('li'), span = document.createElement('span'), remove = document.createElement('button');
      span.textContent = artist ? artist.nombre + ' · #' + id + (artist.genero ? ' · ' + artist.genero : '') : 'Artista #' + id + ' · datos no disponibles';
      remove.type = 'button'; remove.className = 'text-link'; remove.textContent = 'Quitar artista favorito'; remove.disabled = prefsBusy || artistsBusy || !ready;
      remove.addEventListener('click', () => mutate(id, false)); item.append(span, remove); list.append(item);
    }
    if (!ids.length) { const item = document.createElement('li'); item.textContent = ready ? 'No hay artistas favoritos guardados en el servidor.' : 'Artistas favoritos sin verificar.'; list.append(item); }
  }
  async function loadPreferences() {
    if (prefsBusy || artistsBusy || !active()) return;
    prefsBusy = true; fields.disabled = true; reload.disabled = true; status.textContent = 'Cargando preferencias…';
    try { const data = parsePreferences(await get(prefix + '/preferencias')); if (!active()) return; apply(data); ids = data.artistasFavoritos; ready = true; fields.disabled = false; status.textContent = 'Preferencias del servidor cargadas. Guardar reemplaza los tipos y la ciudad preferida completos.'; }
    catch (cause) { ready = false; status.textContent = cause instanceof Error ? cause.message : 'No pudimos consultar preferencias.'; }
    finally { prefsBusy = false; reload.disabled = false; paintArtists(); }
  }
  async function loadArtists() {
    if (artistsBusy || !active()) return;
    artistsBusy = true; artistReload.disabled = true; artistStatus.textContent = 'Consultando catálogo y favoritos del servidor…'; paintArtists();
    const results = await Promise.allSettled([get('/api/artistas'), get(prefix + '/artistas-favoritos'), get(prefix + '/preferencias')]);
    if (!active()) return;
    const errors: string[] = [];
    try { const result = results[0]; if (result.status === 'rejected') throw result.reason; artists = parseArtists(result.value); } catch (cause) { artists = []; errors.push('Catálogo: ' + (cause instanceof Error ? cause.message : 'no disponible')); }
    try { const result = results[1]; if (result.status === 'rejected') throw result.reason; resolved = parseArtists(result.value, true); } catch (cause) { resolved = []; errors.push('Datos de favoritos: ' + (cause instanceof Error ? cause.message : 'no disponibles') + '. Podés quitar los IDs recuperados de preferencias.'); }
    try { const result = results[2]; if (result.status === 'rejected') throw result.reason; ids = parsePreferences(result.value).artistasFavoritos; ready = true; } catch (cause) { ready = false; errors.push('IDs favoritos sin verificar: ' + (cause instanceof Error ? cause.message : 'falló la consulta')); }
    artistsBusy = false; artistReload.disabled = false; artistStatus.textContent = errors.join(' ') || 'Catálogo y artistas favoritos del servidor actualizados.'; paintArtists();
  }
  async function mutate(id: number, adding: boolean) {
    if (!active() || artistsBusy || prefsBusy || !ready || adding && !artists.some(a => a.id === id)) return;
    artistsBusy = true; reload.disabled = true; artistReload.disabled = true; paintArtists();
    artistStatus.textContent = adding ? 'Agregando artista favorito…' : 'Quitando artista favorito…';
    let confirmed = false;
    try {
      await api(prefix + '/artistas-favoritos/' + id, { method: adding ? 'PUT' : 'DELETE' }); confirmed = true;
    } catch (cause) { artistStatus.textContent = (cause instanceof Error ? cause.message : 'No se pudo leer el resultado.') + ' Se consultarán los IDs del servidor para confirmar el estado.'; }
    if (!active()) return;
    try {
      const data = parsePreferences(await get(prefix + '/preferencias')); if (!active()) return;
      ids = data.artistasFavoritos; ready = true;
      if (confirmed) { artistStatus.textContent = 'Operación confirmada. Artistas favoritos del servidor actualizados.'; window.dispatchEvent(new Event('ticketya:recommendations-refresh')); }
      else artistStatus.textContent += ' El estado actual ya fue leído y se muestra en la lista.';
    } catch (cause) { ready = false; artistStatus.textContent += ' No pudimos verificar los favoritos actuales; recargá antes de continuar.'; }
    finally { artistsBusy = false; reload.disabled = false; artistReload.disabled = false; paintArtists(); }
  }
  form.addEventListener('submit', async event => {
    event.preventDefault(); if (prefsBusy || fields.disabled || !active()) return;
    let body: ReturnType<typeof preferencesBody>;
    try { body = preferencesBody([...types.querySelectorAll<HTMLInputElement>('input')].filter(box => box.checked).map(box => box.value), city.value); }
    catch (cause) { status.textContent = cause instanceof Error ? cause.message : 'Revisá las preferencias.'; return; }
    prefsBusy = true; fields.disabled = true; reload.disabled = true; form.setAttribute('aria-busy','true'); paintArtists(); status.textContent = 'Guardando ambos campos de preferencias…';
    try {
      const { data } = await api<{ id: number; tiposEventoPreferidos: string[]; ciudadPreferida: string }>(prefix + '/preferencias', { method: 'PUT', body });
      if (!active()) return;
      if (!data || data.id !== owner) throw new Error('No pudimos verificar el perfil devuelto. Consultá las preferencias antes de reenviar.');
      const result = parsePreferences({ tiposEventoPreferidos: data.tiposEventoPreferidos, ciudadPreferida: data.ciudadPreferida, artistasFavoritos: ids });
      apply(result); status.textContent = 'Tipos y ciudad preferida guardados en el servidor.'; window.dispatchEvent(new Event('ticketya:recommendations-refresh'));
    } catch (cause) { status.textContent = (cause instanceof Error ? cause.message : 'No se pudo confirmar el resultado.') + ' Se conserva tu edición. Podés recargar para consultar el estado del servidor.'; }
    finally { prefsBusy = false; fields.disabled = false; reload.disabled = false; form.removeAttribute('aria-busy'); paintArtists(); }
  });
  choices.addEventListener('change', paintArtists);
  add.addEventListener('click', () => { const id = Number(choices.value); if (!add.disabled) mutate(id, true); });
  reload.addEventListener('click', loadPreferences); artistReload.addEventListener('click', loadArtists);
  // La lectura de favoritos no modifica la edición de preferencias en curso.
  loadPreferences(); loadArtists();
}
