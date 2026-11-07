import QRCode from 'qrcode';
import {catalog} from '../data/events';
import {demoTickets,type DemoTicket,type TicketStatus} from '../data/tickets';
import {canRefund,getRefunds,requestRefund,statusOf} from './refunds';

const money=(n:number)=>'$ '+n.toLocaleString('es-AR');
const esc=(s:unknown)=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!));
const date=(value:string)=>new Intl.DateTimeFormat('es-AR',{dateStyle:'long',timeStyle:'short',timeZone:'America/Argentina/Buenos_Aires'}).format(new Date(value));
const labels:Record<TicketStatus,string>={valida:'Válida',utilizada:'Utilizada',pendiente:'Devolución pendiente',devuelta:'Devuelta'};
export function initTickets(){
 let filter='todas',purchase:string|null=null,activeRefund:string|null=null,qrVersion=0,activeQr:string|null=null;
 const qrDialog=document.querySelector<HTMLDialogElement>('#ticket-qr-dialog')!;
 const refundDialog=document.querySelector<HTMLDialogElement>('#ticket-refund-dialog')!;
 const status=document.querySelector<HTMLElement>('#tickets-status')!;
 function paint(){
  const refunds=getRefunds();const statuses=demoTickets.map(statusOf);
  document.querySelector('#valid-tickets')!.textContent=String(statuses.filter(s=>s==='valida').length);
  document.querySelector('#pending-tickets')!.textContent=String(statuses.filter(s=>s==='pendiente').length);
  document.querySelector('#other-tickets')!.textContent=String(statuses.filter(s=>s==='utilizada'||s==='devuelta').length);
  document.querySelectorAll('[data-entry-count]').forEach(e=>e.textContent=String(demoTickets.length));
  const items=demoTickets.filter(t=>(filter==='todas'||statusOf(t)===filter)&&(!purchase||t.purchaseId===purchase));
  const list=document.querySelector('#ticket-list')!;
  list.innerHTML=items.map(ticket=>{
   const e=catalog.find(e=>e.id===ticket.eventId)!;const state=statusOf(ticket);const eligible=canRefund(ticket);const request=refunds.find(r=>r.ticketId===ticket.id);
   return `<article class="entry-card" data-ticket-id="${ticket.id}"><div class="entry-photo"><img src="${esc(e.image)}" alt="" loading="lazy"/><span>ENTRADA DE EJEMPLO</span></div><div class="entry-content"><div class="entry-topline"><span>${esc(ticket.purchaseId)}</span><span class="entry-badge status-${state}">${labels[state]}</span></div><h3>${esc(e.title)}</h3><p class="entry-date">${esc(date(ticket.eventAt))}</p><p class="entry-location">${esc(e.venue)} · ${esc(e.city)}</p><div class="entry-seat"><strong>${esc(ticket.sector)}</strong><span>${esc(ticket.seat)}</span></div><div class="entry-footer"><span class="entry-id">${ticket.id}</span><strong>${money(ticket.price)}</strong></div><div class="entry-actions"><button class="entry-qr" data-qr="${ticket.id}" ${state!=='valida'?'disabled':''}>${state==='valida'?'▦ Ver QR de ejemplo':labels[state]}</button><button class="entry-refund" data-refund="${ticket.id}" ${!eligible?'disabled':''}>Solicitar devolución</button></div><p class="entry-note">${state==='pendiente'?`Solicitud de ejemplo enviada el ${esc(date(request!.requestedAt))}. Pendiente de aprobación.`:state==='utilizada'?'Esta entrada de ejemplo ya fue utilizada.':state==='devuelta'?'Devolución finalizada en este ejemplo.':!eligible?'El plazo de devolución finalizó: faltan menos de 72 horas para el evento.':'Devolución disponible hasta 72 horas antes del evento.'}</p></div></article>`;
  }).join('');
  document.querySelector<HTMLElement>('#tickets-empty')!.hidden=items.length>0;
  document.querySelector<HTMLElement>('#purchase-ticket-filter')!.hidden=!purchase;
  document.querySelector('#purchase-ticket-label')!.textContent=purchase?`Entradas de ${purchase}`:'';
  list.querySelectorAll<HTMLButtonElement>('[data-qr]').forEach(b=>b.addEventListener('click',()=>showQr(b.dataset.qr!)));
  list.querySelectorAll<HTMLButtonElement>('[data-refund]').forEach(b=>b.addEventListener('click',()=>openRefund(b.dataset.refund!)));
  if(activeQr&&statusOf(demoTickets.find(t=>t.id===activeQr)!)!=='valida')qrDialog.close();
 }
 async function showQr(id:string){
  const ticket=demoTickets.find(t=>t.id===id);if(!ticket||statusOf(ticket)!=='valida')return;
  const e=catalog.find(e=>e.id===ticket.eventId)!;activeQr=id;const version=++qrVersion;
  document.querySelector('#qr-title')!.textContent=e.title;
  document.querySelector('#qr-location')!.textContent=`${ticket.sector} · ${ticket.seat}`;
  document.querySelector('#qr-date')!.textContent=date(ticket.eventAt);
  document.querySelector('#qr-ticket-id')!.textContent=ticket.id;
  const img=document.querySelector<HTMLImageElement>('#ticket-qr-image')!;img.hidden=true;img.removeAttribute('src');
  const loading=document.querySelector<HTMLElement>('#qr-loading')!;loading.textContent='Preparando el QR…';loading.hidden=false;qrDialog.showModal();
  try{const url=await QRCode.toDataURL(`TICKETYA-DEMO|${id}|SIN-VALIDEZ-PARA-INGRESAR`,{width:320,margin:4,errorCorrectionLevel:'M',color:{dark:'#172039',light:'#ffffff'}});if(qrVersion!==version||!qrDialog.open)return;img.src=url;img.hidden=false;loading.hidden=true;}
  catch{if(qrVersion===version)loading.textContent='No pudimos preparar el QR de ejemplo. Cerrá la ventana y volvé a intentarlo.';}
 }
 function openRefund(id:string){const ticket=demoTickets.find(t=>t.id===id);if(!ticket||!canRefund(ticket)){paint();status.textContent='Esta entrada no cumple las condiciones para solicitar una devolución.';return;}activeRefund=id;const e=catalog.find(e=>e.id===ticket.eventId)!;document.querySelector('#refund-event')!.textContent=e.title;document.querySelector('#refund-seat')!.textContent=`${ticket.sector} · ${ticket.seat}`;document.querySelector('#refund-amount')!.textContent=money(ticket.price);document.querySelector<HTMLFormElement>('#refund-form')!.reset();document.querySelector<HTMLElement>('#refund-error')!.hidden=true;document.querySelector<HTMLButtonElement>('#confirm-refund')!.disabled=false;refundDialog.showModal();}
 document.querySelectorAll<HTMLButtonElement>('[data-ticket-filter]').forEach(button=>button.addEventListener('click',()=>{filter=button.dataset.ticketFilter!;document.querySelectorAll('[data-ticket-filter]').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));paint();}));
 document.querySelector('#clear-purchase-ticket')!.addEventListener('click',()=>{purchase=null;paint();});
 document.addEventListener('click',e=>{const target=(e.target as HTMLElement).closest<HTMLButtonElement>('[data-purchase-tickets]');if(!target)return;purchase=target.dataset.purchaseTickets!;filter='todas';document.querySelectorAll<HTMLElement>('[data-ticket-filter]').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.ticketFilter==='todas')));document.querySelector<HTMLDialogElement>('#purchase-dialog')!.close();document.querySelector<HTMLButtonElement>('[data-tab="entradas"]')!.click();paint();});
 document.querySelector<HTMLFormElement>('#refund-form')!.addEventListener('submit',e=>{
  e.preventDefault();if(!activeRefund)return;const button=document.querySelector<HTMLButtonElement>('#confirm-refund')!;if(button.disabled)return;button.disabled=true;
  try{requestRefund(activeRefund,document.querySelector<HTMLSelectElement>('#refund-reason')!.value);activeRefund=null;refundDialog.close();paint();status.textContent='Solicitud de devolución de ejemplo guardada. Queda pendiente de aprobación del administrador; no se realizó ningún reintegro.';}
  catch(cause){const error=document.querySelector<HTMLElement>('#refund-error')!;error.textContent=cause instanceof Error&&cause.message==='not-eligible'?'El plazo o estado de esta entrada ya no permite solicitar la devolución.':'No pudimos guardar la solicitud en este navegador. No se confirmó ninguna devolución.';error.hidden=false;button.disabled=false;paint();}
 });
 document.querySelectorAll('[data-close-qr]').forEach(b=>b.addEventListener('click',()=>qrDialog.close()));qrDialog.addEventListener('close',()=>{qrVersion++;activeQr=null;});
 document.querySelectorAll('[data-close-refund]').forEach(b=>b.addEventListener('click',()=>refundDialog.close()));refundDialog.addEventListener('close',()=>activeRefund=null);
 window.addEventListener('storage',()=>paint());document.addEventListener('visibilitychange',()=>{if(!document.hidden)paint();});setInterval(paint,60000);paint();
}
