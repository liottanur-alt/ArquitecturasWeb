import { catalog } from '../data/events';
import { getSession } from './session';
export type Draft = { eventId: number; selection: { sectorId: string; quantity: number; seats: string[] }[]; updatedAt: string };
function owner() { const session = getSession(); if (!session) return 'invitado'; try { const sub = JSON.parse(atob(session.accessToken.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'))).sub; return /^\d+$/.test(sub) ? `usuario-${sub}` : `email-${session.email.toLowerCase()}`; } catch { return `email-${session.email.toLowerCase()}`; } }
const key = () => `ticketya-carrito-${owner()}`;
export function normalizeDraft(value: unknown): Draft | null {
    if (!value || typeof value !== 'object') return null; const v = value as Draft; const event = catalog.find(e => e.id === v.eventId); if (!event || !Array.isArray(v.selection)) return null;
    let total = 0; const used = new Set<string>(); const selection: Draft['selection'] = [];
    for (const item of v.selection) {
        if (!item || used.has(item.sectorId)) return null; used.add(item.sectorId); const sector = event.sectors.find(s => s.id === item.sectorId);
        if (!sector || !Number.isInteger(item.quantity) || item.quantity < 1 || item.quantity > Math.min(event.maxTickets, sector.available) || !Array.isArray(item.seats)) return null;
        if (sector.type === 'general' && item.seats.length !== 0) return null;
        if (sector.type === 'numerado') {
            if (item.seats.length !== item.quantity || new Set(item.seats).size !== item.seats.length) return null;
            for (const seat of item.seats) { if (typeof seat !== 'string' || !/^\w-\d+$/.test(seat)) return null; const [row, n] = seat.split('-'); if (row < 'A' || row >= String.fromCharCode(65 + sector.rows!) || Number(n) < 1 || Number(n) > sector.columns! || sector.occupied!.includes(seat)) return null; }
        }
        total += item.quantity; selection.push({ sectorId: sector.id, quantity: item.quantity, seats: [...item.seats] });
    }
    if (total === 0 || total > event.maxTickets) return null;
    return { eventId: event.id, selection, updatedAt: typeof v.updatedAt === 'string' && !Number.isNaN(Date.parse(v.updatedAt)) ? v.updatedAt : new Date().toISOString() };
}
export function getCart(): Draft[] { try { const values: unknown = JSON.parse(localStorage.getItem(key()) || '[]'); if (!Array.isArray(values)) return []; const unique = new Set<number>(); return values.slice(0, 30).map(normalizeDraft).filter((v): v is Draft => !!v && !unique.has(v.eventId) && !!unique.add(v.eventId)); } catch { return []; } }
export function saveDraft(value: Draft) { const draft = normalizeDraft(value); if (!draft) throw new Error('Seleccion invalida'); const items = getCart().filter(d => d.eventId !== draft.eventId); localStorage.setItem(key(), JSON.stringify([draft, ...items])); }
export function removeDraft(id: number) { localStorage.setItem(key(), JSON.stringify(getCart().filter(d => d.eventId !== id))); }
export function seedDemoCart() { if (getSession()) return; try { if (localStorage.getItem(key()) !== null) return; saveDraft({ eventId: 1, selection: [{ sectorId: 'campo', quantity: 2, seats: [] }], updatedAt: new Date().toISOString() }); saveDraft({ eventId: 2, selection: [{ sectorId: 'platea', quantity: 1, seats: ['A-1'] }], updatedAt: new Date().toISOString() }); } catch { } }
export function cartLink() { return getSession() ? '/perfil/#carrito' : '/perfil/?demo=1#carrito'; }
