import {getSession} from './session';
import {demoTickets,type DemoTicket,type TicketStatus} from '../data/tickets';
export const REFUND_WINDOW=72*60*60*1000;
export type Refund={ticketId:string;requestedAt:string;reason:string};
export const reasons=['No puedo asistir','Cambio de planes','Otro motivo'];
export function refundKey(){const session=getSession();if(!session)return 'ticketya-devoluciones-demo-invitado';let subject=session.email.toLowerCase();try{const sub=JSON.parse(atob(session.accessToken.split('.')[1].replace(/-/g,'+').replace(/_/g,'/'))).sub;if(/^\d+$/.test(sub))subject=`usuario-${sub}`;}catch{}return `ticketya-devoluciones-demo-${subject}`;}
export function getRefunds():Refund[]{try{const raw:unknown=JSON.parse(localStorage.getItem(refundKey())||'[]');if(!Array.isArray(raw))return [];const ids=new Set<string>();return raw.filter((value):value is Refund=>{if(!value||typeof value!=='object')return false;const v=value as Refund;const ticket=demoTickets.find(t=>t.id===v.ticketId);if(!ticket||ticket.status!=='valida'||ids.has(v.ticketId)||typeof v.requestedAt!=='string'||!Number.isFinite(Date.parse(v.requestedAt))||!reasons.includes(v.reason))return false;ids.add(v.ticketId);return true;});}catch{return [];}}
export function statusOf(ticket:DemoTicket):TicketStatus{return getRefunds().some(r=>r.ticketId===ticket.id)?'pendiente':ticket.status;}
export function canRefund(ticket:DemoTicket,now=Date.now()){return statusOf(ticket)==='valida'&&Date.parse(ticket.eventAt)-now>=REFUND_WINDOW;}
export function requestRefund(ticketId:string,reason:string,now=Date.now()){
 const ticket=demoTickets.find(t=>t.id===ticketId);
 if(!ticket||!canRefund(ticket,now))throw new Error('not-eligible');
 if(!reasons.includes(reason))throw new Error('invalid-reason');
 const refund={ticketId,reason,requestedAt:new Date(now).toISOString()};
 localStorage.setItem(refundKey(),JSON.stringify([...getRefunds(),refund]));
 return refund;
}
