export type Recommendation = { idEvento: number; nombre: string; tipo: string; fechaHora: string; ciudad: string; puntuacion: number; motivos: string[]; fuentes: string[] };
export type Recommendations = { recomendaciones: Recommendation[]; parcial: boolean; fuentesNoDisponibles: string[]; historialVerificado: boolean; disponibilidadVerificada: boolean };
const strings = (value: unknown): value is string[] => Array.isArray(value) && value.every(item => typeof item === 'string');
export function parseRecommendations(value: unknown): Recommendations {
  const r = value as Recommendations;
  if (!r || !['parcial','historialVerificado','disponibilidadVerificada'].every(key => typeof r[key as keyof Recommendations] === 'boolean')
    || !strings(r.fuentesNoDisponibles) || !Array.isArray(r.recomendaciones)
    || !r.recomendaciones.every(e => e && Number.isSafeInteger(e.idEvento) && e.idEvento > 0 && typeof e.nombre === 'string' && typeof e.tipo === 'string'
      && typeof e.fechaHora === 'string' && typeof e.ciudad === 'string' && typeof e.puntuacion === 'number' && Number.isFinite(e.puntuacion)
      && strings(e.motivos) && strings(e.fuentes))) throw new Error('La respuesta de recomendaciones no permite verificar sus datos o advertencias.');
  if (new Set(r.recomendaciones.map(e => e.idEvento)).size !== r.recomendaciones.length) throw new Error('Se recibieron recomendaciones duplicadas.');
  return r;
}
export function recommendationWarnings(r: Recommendations) {
  const warnings: string[] = [];
  if (r.parcial) warnings.push('Respuesta parcial: algunas fuentes no pudieron contribuir.');
  if (r.fuentesNoDisponibles.length) warnings.push('Fuentes no disponibles: ' + r.fuentesNoDisponibles.join(', ') + '.');
  if (!r.historialVerificado) warnings.push('Historial sin verificar: pueden aparecer eventos que ya compraste.');
  if (!r.disponibilidadVerificada) warnings.push('Disponibilidad sin verificar: no se pudo comprobar el cupo de estos eventos.');
  return warnings;
}
