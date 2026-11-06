import gsap from 'gsap';
import {getCart,saveDraft,cartLink} from './cart';
import type { Sector } from '../data/events';

const event: { id:number; sectors:Sector[]; maxTickets:number } = JSON.parse(document.querySelector('#event-data')!.textContent!);
const selected = new Map<string, { quantity:number; seats:string[] }>(event.sectors.map(s => [s.id,{quantity:0,seats:[]}]));
const reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
const money = (n:number) => '$ ' + n.toLocaleString('es-AR');
const count = () => [...selected.values()].reduce((n,s)=>n+s.quantity,0);
const message = document.querySelector<HTMLElement>('#selection-message')!;
const review = document.querySelector<HTMLButtonElement>('#review-selection')!;
const dialog = document.querySelector<HTMLDialogElement>('#review-dialog')!;
function update() {
  const entries = event.sectors.filter(s => selected.get(s.id)!.quantity > 0);
  const container = document.querySelector<HTMLElement>('#summary-items')!;
  container.replaceChildren();
  let total = 0;
  for (const sector of entries) {
    const value=selected.get(sector.id)!;
    total += value.quantity * sector.price;
    const item=document.createElement('div'); item.className='summary-item';
    const heading=document.createElement('strong'); heading.textContent=sector.name;
    const detail=document.createElement('span'); detail.textContent=sector.type==='numerado'
      ? value.seats.map(s => {const [row,seat]=s.split('-'); return `Fila ${row} · ${seat}`;}).join(' / ')
      : `${value.quantity} ${value.quantity===1?'entrada':'entradas'} · Ubicación libre`;
    const price=document.createElement('span'); price.className='summary-item-price'; price.textContent=`${value.quantity} × ${money(sector.price)}`;
    item.append(heading,detail,price); container.append(item);
  }
  document.querySelector<HTMLElement>('#summary-empty')!.hidden=entries.length>0;
  document.querySelector('#ticket-count')!.textContent=`(${count()})`;
  document.querySelector('#subtotal')!.textContent=money(total);
  document.querySelector('#total')!.textContent=money(total);
  review.disabled=count()===0;
  document.querySelector<HTMLElement>('#clear-selection')!.hidden=count()===0;
  event.sectors.forEach(sector=>{
    const value=selected.get(sector.id)!;
    const output=document.querySelector(`[data-quantity="${sector.id}"]`);
    if(output) output.textContent=String(value.quantity);
    const minus=document.querySelector<HTMLButtonElement>(`[data-minus="${sector.id}"]`);
    const plus=document.querySelector<HTMLButtonElement>(`[data-plus="${sector.id}"]`);
    if(minus) minus.disabled=value.quantity===0;
    if(plus) plus.disabled=count()>=event.maxTickets || value.quantity>=sector.available;
    document.querySelectorAll<HTMLButtonElement>(`[data-seat-sector="${sector.id}"]`).forEach(button=>{
      const isSelected=value.seats.includes(button.dataset.seat!);
      button.setAttribute('aria-pressed',String(isSelected));
      button.classList.toggle('is-selected',isSelected);
    });
  });
  document.querySelector('#review-items')!.replaceChildren(...[...container.childNodes].map(node=>node.cloneNode(true)));
  document.querySelector('#review-total')!.textContent=money(total);
}
document.querySelectorAll<HTMLButtonElement>('[data-sector]').forEach(button=>button.addEventListener('click',()=>{
  const id=button.dataset.sector!;
  document.querySelectorAll<HTMLButtonElement>('[data-sector]').forEach(b=>{const active=b===button;b.classList.toggle('selected',active);b.setAttribute('aria-pressed',String(active));});
  document.querySelectorAll<HTMLElement>('[data-panel]').forEach(panel=>{
    panel.hidden=panel.dataset.panel!==id;
    if(!panel.hidden&&!reduced.matches) gsap.fromTo(panel,{opacity:0,y:12},{opacity:1,y:0,duration:.35,clearProps:'all'});
  });
  message.textContent='';
}));
document.querySelectorAll<HTMLButtonElement>('[data-plus]').forEach(button=>button.addEventListener('click',()=>{
  const id=button.dataset.plus!, value=selected.get(id)!, sector=event.sectors.find(s=>s.id===id)!;
  if(count()>=event.maxTickets||value.quantity>=sector.available) return;
  value.quantity++;message.textContent=`${count()} de ${event.maxTickets} entradas seleccionadas.`;update();
}));
document.querySelectorAll<HTMLButtonElement>('[data-minus]').forEach(button=>button.addEventListener('click',()=>{
  const value=selected.get(button.dataset.minus!)!;
  value.quantity=Math.max(0,value.quantity-1);message.textContent=`${count()} de ${event.maxTickets} entradas seleccionadas.`;update();
}));
document.querySelectorAll<HTMLButtonElement>('[data-seat]').forEach(button=>button.addEventListener('click',()=>{
  if(button.disabled)return;
  const value=selected.get(button.dataset.seatSector!)!, seat=button.dataset.seat!;
  if(value.seats.includes(seat)) value.seats=value.seats.filter(s=>s!==seat);
  else {
    if(count()>=event.maxTickets){message.textContent=`Podés elegir hasta ${event.maxTickets} entradas en total. Quitá una para cambiar tu selección.`;return;}
    value.seats.push(seat);
    if(!reduced.matches) gsap.fromTo(button,{scale:.8},{scale:1,duration:.3,ease:'back.out(2)'});
  }
  value.quantity=value.seats.length;message.textContent=`${count()} de ${event.maxTickets} entradas seleccionadas.`;update();
}));
document.querySelector('#clear-selection')!.addEventListener('click',()=>{
  selected.forEach(value=>{value.quantity=0;value.seats=[];});message.textContent='Selección vacía. Podés empezar de nuevo.';update();
});
review.addEventListener('click',()=>{if(count()>0)dialog.showModal();});
document.querySelector('#close-review')!.addEventListener('click',()=>dialog.close());
document.querySelector('#keep-choosing')!.addEventListener('click',()=>dialog.close());
dialog.addEventListener('click',e=>{const r=dialog.getBoundingClientRect();if(e.target===dialog&&(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom))dialog.close();});

if(new URLSearchParams(location.search).get('carrito')==='1'){
 const draft=getCart().find(d=>d.eventId===event.id);
 if(draft){draft.selection.forEach(item=>selected.set(item.sectorId,{quantity:item.quantity,seats:[...item.seats]}));const first=draft.selection[0];document.querySelector<HTMLButtonElement>(`[data-sector="${first.sectorId}"]`)?.click();message.textContent='Recuperamos tu selección del carrito. Revisá tus ubicaciones antes de continuar.';}
}
document.querySelector('#save-cart')!.addEventListener('click',()=>{
 if(count()===0)return;
 try{saveDraft({eventId:event.id,selection:[...selected.entries()].filter(([,value])=>value.quantity>0).map(([sectorId,value])=>({sectorId,quantity:value.quantity,seats:[...value.seats]})),updatedAt:new Date().toISOString()});document.querySelector('#save-cart-status')!.textContent='Selección guardada en este navegador. Todavía no se reservaron entradas.';const link=document.querySelector<HTMLAnchorElement>('#view-cart')!;link.href=cartLink();link.hidden=false;}
 catch{document.querySelector('#save-cart-status')!.textContent='No pudimos guardar la selección en este navegador. No se modificó tu compra.';}
});

update();
if(!reduced.matches)gsap.from('.banner-content > *, .about-event, .booking-heading, .order-summary',{y:20,opacity:0,duration:.65,stagger:.06,ease:'power3.out',clearProps:'all'});
