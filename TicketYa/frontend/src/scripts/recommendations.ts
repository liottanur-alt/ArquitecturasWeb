import { api } from './api';
import { getAuthenticatedUserId } from './session';
import { latestRequest } from './catalog-search';
import { catalogDate } from './catalog-data';
import { eventLink } from './selection-data';
import { parseRecommendations, recommendationWarnings } from './recommendations-data';

export function initRecommendations(owner: number) {
  const list = document.querySelector<HTMLElement>('#recommendation-list')!;
  const warnings = document.querySelector<HTMLElement>('#recommendation-warnings')!;
  const status = document.querySelector<HTMLElement>('#recommendation-status')!;
  const verified = document.querySelector<HTMLElement>('#recommendation-verifications')!;
  const reload = document.querySelector<HTMLButtonElement>('#reload-recommendations')!;
  const latest = latestRequest();
  let version = 0;
  const text = (tag: string, value: string, className = '') => { const node = document.createElement(tag); node.textContent = value; node.className = className; return node; };
  async function load() {
    if (getAuthenticatedUserId() !== owner) return;
    const current = ++version;
    list.replaceChildren(); warnings.replaceChildren(); verified.textContent = '';
    reload.disabled = true; list.setAttribute('aria-busy','true'); status.textContent = 'Consultando recomendaciones para este perfil…';
    try {
      const result = await latest(signal => api('/usuarios/' + owner + '/recomendaciones', { signal, cache: 'no-store' }));
      if (!result.current || getAuthenticatedUserId() !== owner) return;
      const response = parseRecommendations(result.data.data);
      for (const warning of recommendationWarnings(response)) warnings.append(text('li', warning));
      verified.textContent = 'Historial: ' + (response.historialVerificado ? 'verificado al consultar' : 'sin verificar') + ' · Disponibilidad: ' + (response.disponibilidadVerificada ? 'verificada al consultar; puede cambiar' : 'sin verificar') + '.';
      status.textContent = response.recomendaciones.length ? 'Recomendaciones recibidas: ' + response.recomendaciones.length + '. Orden proporcionado por el servidor.'
        : recommendationWarnings(response).length ? 'No se recibieron recomendaciones en esta respuesta limitada. Revisá las advertencias antes de interpretar el resultado.' : 'No hay recomendaciones para este perfil en esta consulta. Esto no es un error.';
      for (const event of response.recomendaciones) {
        const card = document.createElement('article'); card.className = 'purchase-card';
        const image = document.createElement('img'); image.src = '/event-placeholder.svg'; image.alt = 'Imagen ilustrativa de respaldo'; image.loading = 'lazy';
        const content = document.createElement('div'); content.className = 'purchase-info';
        content.append(text('span', event.tipo + ' · Evento #' + event.idEvento, 'purchase-meta'), text('h3', event.nombre), text('p', catalogDate(event.fechaHora).fullDate), text('p', event.ciudad || 'Ciudad no informada'), text('p', 'Fuentes: ' + (event.fuentes.join(', ') || 'sin fuentes informadas')));
        const reasons = document.createElement('ul'); reasons.setAttribute('aria-label','Motivos de la recomendación');
        for (const reason of event.motivos) reasons.append(text('li', reason));
        if (!event.motivos.length) reasons.append(text('li', 'El servidor no informó motivos para este evento.'));
        content.append(reasons);
        const link = text('a', 'Consultar evento y disponibilidad ↗', 'text-link') as HTMLAnchorElement; link.href = eventLink(event.idEvento); content.append(link);
        card.append(image, content); list.append(card);
      }
    } catch (cause) {
      if (current !== version || getAuthenticatedUserId() !== owner) return;
      status.textContent = 'No pudimos consultar las recomendaciones. ' + (cause instanceof Error ? cause.message : 'Reintentá en unos momentos.');
    } finally { if (current === version && getAuthenticatedUserId() === owner) { reload.disabled = false; list.removeAttribute('aria-busy'); } }
  }
  reload.addEventListener('click', load);
  window.addEventListener('ticketya:recommendations-refresh', load);
  window.addEventListener('pageshow', event => { if (event.persisted) load(); });
  load();
}
