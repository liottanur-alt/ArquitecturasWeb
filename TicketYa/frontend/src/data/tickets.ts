export type TicketStatus='valida'|'utilizada'|'pendiente'|'devuelta';
export type DemoTicket={id:string;purchaseId:string;eventId:number;eventAt:string;sector:string;seat:string;price:number;status:TicketStatus};
export const demoTickets:DemoTicket[]=[
 {id:'TY-DEMO-1042-01',purchaseId:'TY-DEMO-1042',eventId:1,eventAt:'2026-11-21T18:00:00-03:00',sector:'Campo general',seat:'Acceso libre · Entrada 1',price:45000,status:'valida'},
 {id:'TY-DEMO-1042-02',purchaseId:'TY-DEMO-1042',eventId:1,eventAt:'2026-11-21T18:00:00-03:00',sector:'Campo general',seat:'Acceso libre · Entrada 2',price:45000,status:'valida'},
 {id:'TY-DEMO-1031-01',purchaseId:'TY-DEMO-1031',eventId:2,eventAt:'2026-10-31T21:00:00-03:00',sector:'Platea',seat:'Fila B · Asiento 1',price:18000,status:'utilizada'},
 {id:'TY-DEMO-1031-02',purchaseId:'TY-DEMO-1031',eventId:2,eventAt:'2026-10-31T21:00:00-03:00',sector:'Platea',seat:'Fila B · Asiento 2',price:18000,status:'utilizada'},
 {id:'TY-DEMO-1018-01',purchaseId:'TY-DEMO-1018',eventId:6,eventAt:'2026-12-04T22:00:00-03:00',sector:'Sala principal',seat:'Fila C · Asiento 4',price:14000,status:'devuelta'}
];
