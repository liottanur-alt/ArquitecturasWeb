import type { Entry } from './entry-data';
export type RefundRequest = { id: number; idEntrada: number; idCompra: number; idCliente: number; descripcion: string; estadoSolicitud: string; fechaSolicitud: string; fechaResolucion: string | null; estadoReintegro: string | null };
export function parseRefunds(value: unknown, owner: number): RefundRequest[] {
  if (!Array.isArray(value) || !value.every(r => r && [r.id,r.idEntrada,r.idCompra].every((id: unknown) => Number.isSafeInteger(id) && Number(id) > 0) && r.idCliente === owner
    && ['PENDIENTE','ACEPTADA','RECHAZADA','CANCELADA'].includes(r.estadoSolicitud) && typeof r.descripcion === 'string' && typeof r.fechaSolicitud === 'string' && Number.isFinite(Date.parse(r.fechaSolicitud)))) throw new Error('Las solicitudes recibidas son inválidas o de otro perfil.');
  if (new Set(value.map(r => r.id)).size !== value.length) throw new Error('Se recibieron solicitudes duplicadas.');
  return value;
}
export function refundRule(entry: Entry, requests: RefundRequest[], event: unknown, now = Date.now()) {
  if (!['PAGA','DEVOLUCION_RECHAZADA'].includes(entry.estadoEntrada)) return { allowed: false, message: 'El estado actual de la entrada no permite solicitar devolución.' };
  if (requests.some(r => r.idEntrada === entry.id && ['PENDIENTE','ACEPTADA'].includes(r.estadoSolicitud))) return { allowed: false, message: 'La entrada ya tiene una solicitud pendiente o aceptada.' };
  const e = event as { estado?: string; fechaHora?: string } | null;
  if (e && ['CANCELADO','FINALIZADO'].includes(e.estado || '')) return { allowed: false, message: 'El evento está cancelado o finalizado.' };
  const start = e && typeof e.fechaHora === 'string' && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?$/.test(e.fechaHora) ? Date.parse(e.fechaHora + '-03:00') : NaN;
  if (!Number.isFinite(start)) return { allowed: true, message: 'No se pudo verificar la fecha del evento. El servidor comprobará el plazo mínimo de 72 horas.' };
  const deadline = start - 72 * 60 * 60 * 1000;
  return { allowed: now <= deadline, message: 'Plazo: hasta ' + new Date(deadline).toLocaleString('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' }) + ' (Buenos Aires), 72 horas antes del evento.' + (now > deadline ? ' El plazo finalizó.' : ' El servidor confirma la elegibilidad.') };
}
