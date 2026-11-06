const photo = (id: string, width = 900) =>
  `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${width}&q=85`;
export const events = [
  {
    id: 1,
    title: "La noche es nuestra",
    artist: "Festival del Río",
    category: "Música",
    city: "Buenos Aires",
    venue: "Costanera Sur",
    date: "21 NOV",
    fullDate: "21 de noviembre · 18:00 h",
    price: 45000,
    sold: 12480,
    high: true,
    seating: "Campo + plateas numeradas",
    image: photo("photo-1470229722913-7c0e2dbbafd3", 1800),
    label: "UN ESCENARIO. MIL HISTORIAS.",
    subtitle: "Esa canción que querías vivir. Ese momento que no se repite.",
  },
  {
    id: 2,
    title: "Fuera de libreto",
    artist: "Teatro en movimiento",
    category: "Teatro",
    city: "Tandil",
    venue: "Teatro del Fuerte",
    date: "28 NOV",
    fullDate: "28 de noviembre · 21:00 h",
    price: 18000,
    sold: 740,
    high: false,
    seating: "Asientos numerados",
    image: photo("photo-1503095396549-807759245b35", 1800),
    label: "LA EMOCIÓN, EN PRIMERA FILA.",
    subtitle: "Una historia para dejar el mundo afuera por un rato.",
  },
  {
    id: 3,
    title: "El próximo capítulo",
    artist: "Ideas que conectan",
    category: "Conferencias",
    city: "Buenos Aires",
    venue: "Centro de Convenciones",
    date: "05 DIC",
    fullDate: "5 de diciembre · 09:00 h",
    price: 24000,
    sold: 3200,
    high: true,
    seating: "Sectores sin numerar",
    image: photo("photo-1540575467063-178a50c2df87", 1800),
    label: "LAS IDEAS TAMBIÉN SE VIVEN.",
    subtitle: "Vení por una charla. Andate con una nueva perspectiva.",
  },
  {
    id: 4,
    title: "Bajo las estrellas",
    artist: "Indie al aire libre",
    category: "Música",
    city: "Tandil",
    venue: "Anfiteatro Martín Fierro",
    date: "12 DIC",
    fullDate: "12 de diciembre · 19:00 h",
    price: 22000,
    sold: 4800,
    high: true,
    seating: "Campo sin numerar",
    image: photo("photo-1459749411175-04bf5292ceea"),
    label: "",
    subtitle: "",
  },
  {
    id: 5,
    title: "Se juega en casa",
    artist: "Copa de Verano",
    category: "Deportes",
    city: "Mar del Plata",
    venue: "Estadio José María Minella",
    date: "19 DIC",
    fullDate: "19 de diciembre · 20:00 h",
    price: 16000,
    sold: 8900,
    high: true,
    seating: "Popular + plateas numeradas",
    image: photo("photo-1522778119026-d647f0596c20"),
    label: "",
    subtitle: "",
  },
  {
    id: 6,
    title: "Un poco de todo",
    artist: "Stand up de viernes",
    category: "Comedia",
    city: "Buenos Aires",
    venue: "Club de la Comedia",
    date: "04 DIC",
    fullDate: "4 de diciembre · 22:00 h",
    price: 14000,
    sold: 950,
    high: false,
    seating: "Asientos numerados",
    image: photo("photo-1516280440614-37939bbacd81"),
    label: "",
    subtitle: "",
  },
];

export type Sector = {
  id: string; name: string; type: 'general' | 'numerado'; price: number;
  available: number; description: string; rows?: number; columns?: number; occupied?: string[];
};
const general = (id: string, name: string, price: number, available: number): Sector => ({
  id, name, price, available, type: 'general', description: 'Ubicación libre dentro del sector. Sin asiento asignado.'
});
const numbered = (id: string, name: string, price: number): Sector => ({
  id, name, price, available: 62, type: 'numerado', description: 'Elegí tu fila y asiento en el mapa.',
  rows: 6, columns: 12, occupied: ['A-3','A-4','A-9','B-5','B-6','C-2','C-3','C-8','D-10','E-7']
});
const sectors: Record<number, Sector[]> = {
  1: [general('campo','Campo general',45000,120), numbered('platea','Platea preferencial',68000), numbered('lateral','Platea lateral',55000)],
  2: [numbered('platea','Platea',18000), numbered('palco','Palco',24000)],
  3: [general('general','Acceso general',24000,86), general('preferencial','Acceso preferencial',36000,22)],
  4: [general('campo','Campo general',22000,140), general('frente','Campo delantero',32000,45)],
  5: [general('popular','Popular',16000,250), numbered('platea','Platea',28000)],
  6: [numbered('sala','Sala principal',14000), numbered('premium','Primeras filas',20000)]
};
export const catalog = events.map(event => ({
  ...event, sectors: sectors[event.id], maxTickets: 6,
  description: `${event.artist} llega a ${event.venue} para una experiencia que se disfruta en vivo. Elegí cómo querés vivir ${event.title}, encontrá tu lugar y preparate para estar ahí.`,
}));
