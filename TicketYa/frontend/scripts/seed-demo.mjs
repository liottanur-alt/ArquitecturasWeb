// Carga explícita de datos de demostración mediante el Gateway. No borra registros.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const statePath = path.join(root, '.demo-data.json');
const state = fs.existsSync(statePath) ? JSON.parse(fs.readFileSync(statePath,'utf8')) : { attempts: {}, refunds: {} };
const base = (process.env.GATEWAY_URL || 'http://localhost:8080').replace(/\/+$/, '');
const save = () => fs.writeFileSync(statePath, JSON.stringify(state,null,2));
async function api(route, method='GET', body, headers={}) {
  const response = await fetch(base+route, { method, headers: { Accept:'application/json', ...(body === undefined ? {} : {'Content-Type':'application/json'}), ...headers }, body:body === undefined ? undefined : JSON.stringify(body), signal:AbortSignal.timeout(60000) });
  const raw = response.status===204 ? '' : await response.text(); let data = raw; try { data=raw?JSON.parse(raw):null; }catch{}
  if(!response.ok) throw Error(method+' '+route+' → '+response.status+': '+JSON.stringify(data));
  return data;
}
async function ensure(route, match, body) { const existing = (await api(route)).find(match); return existing || await api(route,'POST',body); }
const roles=await api('/roles');
async function user(nombre,email,dni,role,ciudad='Tandil') {
 const idRol=roles.find(r=>r.name===role)?.id; if(!idRol)throw Error('Falta rol '+role);
 return ensure('/usuarios',u=>u.email===email,{nombre,email,dni,ciudad,idRol});
}
const lucia=await user('Lucía · Demo TicketYa','lucia.ticketya.demo@example.com','DEMO-TY-LUCIA','CLIENTE');
const mateo=await user('Mateo · Demo TicketYa','mateo.ticketya.demo@example.com','DEMO-TY-MATEO','CLIENTE','Buenos Aires');
const org=await user('Producción · Demo TicketYa','produccion.ticketya.demo@example.com','DEMO-TY-PROD','ORGANIZADOR');
const places=[];
for(const [name,ciudad,latitud,longitud,tipo] of [['Sala Horizonte','Tandil',-37.3288,-59.1367,'SALA'],['Teatro del Encuentro','Buenos Aires',-34.6037,-58.3816,'TEATRO'],['Espacio Atlántico','Mar del Plata',-38.0055,-57.5426,'ESTADIO']]) {
 const nombre='TicketYa Demo · '+name;
 const place=await ensure('/api/lugares',p=>p.nombre===nombre,{nombre,ciudad,direccion:'Dirección de demostración 123',latitud,longitud,tipo});
 const physical=await api('/sectores?idLugar='+place.id);
 const campo=physical.find(s=>s.nombre==='Campo · Demo') || await api('/sectores','POST',{idLugar:place.id,nombre:'Campo · Demo',tipo:'CAMPO',capacidadTotal:120});
 const numbered=physical.find(s=>s.nombre==='Platea · Demo') || await api('/sectores','POST',{idLugar:place.id,nombre:'Platea · Demo',tipo:'NUMERADO',capacidadTotal:12});
 const seats=await api('/asientos?idSector='+numbered.id);
 for(const fila of ['A','B'])for(let col=1;col<=6;col++)if(!seats.some(s=>s.fila===fila&&s.columna===String(col)))seats.push(await api('/asientos','POST',{idSector:numbered.id,fila,columna:String(col)}));
 places.push({place,campo,numbered,seats});
}
const definitions=[
 ['Noches del Río','RECITAL','Aurora del Sur','Indie rock',0,true,21000,'Una noche de música en vivo para disfrutar con amigos. Evento ficticio para recorrer TicketYa.'],
 ['Fuera de libreto','TEATRO','Compañía Entre Escenas','Teatro',1,false,18000,'Una comedia de encuentros inesperados. Producción de demostración, sin entradas para un espectáculo real.'],
 ['Copa de la Costa','DEPORTE','Club Costa Abierta','Deporte',2,true,15000,'Viví un encuentro deportivo de demostración desde campo o platea.'],
 ['Ideas que conectan','CONFERENCIA','Voces del Futuro','Divulgación',0,false,12000,'Charlas e ideas para imaginar lo que viene. Datos de demostración para probar búsquedas y compras.'],
 ['Bajo las estrellas','FESTIVAL','Colectivo Estelar','Festival',2,true,28000,'Un festival ficticio para compartir una jornada de música y nuevas experiencias.'],
 ['Encuentro de sabores','OTRO','Cocina en Movimiento','Gastronomía',1,false,9000,'Un encuentro gastronómico de demostración. Explorá sectores y guardá una selección sin reservar.']
];
const events=[];
for(let i=0;i<definitions.length;i++) {
 const [title,tipo,artistName,genero,placeIndex,demanda,price,descripcion]=definitions[i], venue=places[placeIndex];
 const nombre='TicketYa Demo · '+title;
 const artist=await ensure('/api/artistas',a=>a.nombre===artistName+' · Demo',{nombre:artistName+' · Demo',genero});
 const existing=await api('/api/eventos/mis-eventos','GET',undefined,{'X-Organizador-Id':String(org.id)});
 let event=existing.find(e=>e.nombre===nombre);
 if(!event){const date=new Date();date.setUTCDate(date.getUTCDate()+30+i*5);const fechaHora=date.toISOString().slice(0,10)+'T21:00:00';event=await api('/api/eventos','POST',{nombre,descripcion,tipo,idOrganizador:org.id,participantesIds:[artist.id],fechaHora,idLugar:venue.place.id,demanda});}
 if(event.estado==='CANCELADO'||event.estado==='FINALIZADO')throw Error('El evento demo existente no admite ventas: '+event.id);
 await api('/eventos/'+event.id+'/inventario','PUT',{limitePorCliente:4});
 await api('/eventos/'+event.id+'/sectores','PUT',{idSector:venue.campo.id,precio:price,capacidadHabilitada:120});
 await api('/eventos/'+event.id+'/sectores','PUT',{idSector:venue.numbered.id,precio:price+6000,capacidadHabilitada:12});
 if(event.estadoPublicacion==='BORRADOR')event=await api('/api/eventos/'+event.id+'/publicar','PATCH',undefined,{'X-Organizador-Id':String(org.id)});
 events.push({event,artist,...venue}); console.log('Evento publicado #'+event.id+': '+nombre);
}
await api('/usuarios/'+lucia.id+'/preferencias','PUT',{tiposEventoPreferidos:['RECITAL','TEATRO','FESTIVAL'],ciudadPreferida:'Tandil'});
await api('/usuarios/'+lucia.id+'/artistas-favoritos/'+events[4].artist.id,'PUT');
await api('/usuarios/'+mateo.id+'/preferencias','PUT',{tiposEventoPreferidos:['FESTIVAL','DEPORTE'],ciudadPreferida:'Mar del Plata'});
async function buy(label,person,index,decision,numbered=false) {
 const item=events[index];
 if(!state.attempts[label]){
  const selection=numbered?{idsAsientos:[item.seats[0].id]}:{cantidadEntradas:1};
  state.attempts[label]={body:{claveIdempotencia:randomUUID(),idCliente:person.id,idEvento:item.event.id,idSector:numbered?item.numbered.id:item.campo.id,medioDePago:'SIMULADO',...selection}};save();
 }
 const attempt=state.attempts[label];
 let purchase=attempt.id?await api('/compras/'+attempt.id):await api('/compras','POST',attempt.body);
 attempt.id=purchase.id;save();
 if(decision!==null&&purchase.estadoCompra==='PENDIENTE')purchase=await api('/compras/'+purchase.id+'/pago?aprobado='+decision,'PUT');
 console.log('Compra demo #'+purchase.id+' · '+person.nombre+' · '+purchase.estadoCompra);
 return purchase;
}
await buy('lucia-qr',lucia,0,true);
const refundPurchase=await buy('lucia-devolucion',lucia,2,true);
const ticket=(await api('/entradas/compra/'+refundPurchase.id))[0];
const requests=await api('/solicitudes/cliente/'+lucia.id);
const existingRefund=requests.find(r=>r.idEntrada===ticket.id);
if(!existingRefund){
 if(state.refunds[ticket.id])throw Error('Existe un envío incierto de devolución. Revisar antes de repetir.');
 state.refunds[ticket.id]=true;save();
 const request=await api('/solicitudes','POST',{idEntrada:ticket.id,idCliente:lucia.id,descripcion:'Solicitud de demostración: no podré asistir al evento ficticio.'});
 state.refunds[ticket.id]=request.id;save();
}
await buy('lucia-rechazada',lucia,3,false);
await buy('mateo-compartida',mateo,0,true);
await buy('mateo-festival',mateo,4,true);
// Pendiente al final para aprovechar sus diez minutos de reserva.
const pending=await buy('lucia-pendiente',lucia,1,null,true);
state.summary={profiles:{lucia:lucia.id,mateo:mateo.id,organizador:org.id},events:events.map(e=>({id:e.event.id,nombre:e.event.nombre})),pendingPurchase:pending.id};save();
const [catalog,history,tickets,recommendations]=await Promise.all([api('/api/eventos'),api('/compras/cliente/'+lucia.id),api('/entradas/cliente/'+lucia.id),api('/usuarios/'+lucia.id+'/recomendaciones')]);
console.log(JSON.stringify({profiles:state.summary.profiles,demoEvents:state.summary.events,pendingPurchase:pending.id,totalPublished:catalog.length,history:history.map(p=>({id:p.id,estado:p.estadoCompra})),entries:tickets.map(e=>({id:e.id,estado:e.estadoEntrada})),recommendations:recommendations.recomendaciones?.length},null,2));
