# Conexión con el Gateway

Resultado de la revisión integrada, correcciones, comandos y límites: [VERIFICACION_RECORRIDO.md](VERIFICACION_RECORRIDO.md).

El cliente compartido está en src/scripts/api.ts. Usar api('/api/eventos'),
api('/usuarios') o api('/eventos/10/sectores'): las rutas son las del Gateway.
Devuelve { data, status, headers }; data es null para 204 o cuerpo vacío.
ApiError conserva status, payload, correlationId y retryAfter; ApiConnectionError
indica conexión, CORS, cancelación o timeout sin resultado confirmado. No reintenta
mutaciones ni agrega tokens. El timeout predeterminado es 12 segundos; se puede
pasar signal. Las lecturas y escrituras de perfil usan este cliente.

En desarrollo, /gateway es exclusivamente el prefijo de transporte del proxy:
/gateway/api/eventos → Gateway /api/eventos
/gateway/usuarios → Gateway /usuarios
/gateway/eventos/10/sectores → Gateway /eventos/10/sectores

GATEWAY_URL configura el destino de Vite (default http://localhost:8080).
PUBLIC_API_BASE_URL configura la base pública del cliente durante el build:
por defecto /gateway; para conexión directa usar el origen del Gateway sin /api.
No incluir secretos en variables PUBLIC_*.

El proxy de Vite funciona en desarrollo. El build estático requiere un reverse
proxy equivalente para /gateway, o PUBLIC_API_BASE_URL con CORS configurado en
el servidor. No se modificó el backend.

/login/ muestra el selector de perfiles de demostración obtenido
con GET /usuarios. /registro/ crea perfiles sin contraseña mediante POST /usuarios,
con el ID de CLIENTE obtenido de GET /roles. Solo se guarda el ID confirmado en sessionStorage, sin token,
contraseña ni vencimiento. Seleccionar un perfil no autentica ni concede permisos.
Mi cuenta permite cambiarlo o quitarlo. /perfil/ (incluido ?demo=1 heredado)
requiere selección y conserva la pestaña de destino al pasar por el selector.
El perfil se consulta y edita en el servidor. El carrito se separa por ID;
los borradores antiguos de invitado no se transfieren.
El inicio consulta GET /api/eventos/buscar y enlaza a /eventos/?id=ID para
seleccionar con detalle e inventario reales. El historial de compras es real. La pestaña de entradas consulta también el servidor.

Verificación: npm run build. Pruebas del cliente: node --experimental-strip-types
--test tests/api.test.mjs (Node compatible con TypeScript stripping).

El catálogo del inicio se obtiene en el navegador mediante el cliente compartido.
Carrusel y tarjetas mapean nombre, descripción, tipo, participantes, lugar y
fechaHora; alta demanda depende solo de demanda=true. El orden es el recibido
del servidor, sin clasificación por ventas. No se muestran precios, ventas ni
cupos. La imagen de respaldo es local e ilustrativa. Fechas sin zona se muestran
como hora de Buenos Aires, independientemente de la zona del navegador.
Categoría (enums) y ciudad completa se envían a GET /api/eventos/buscar.
El texto se aplica localmente solo a nombre, artista y lugar de la página cargada.
Se distinguen carga, error con reintento, catálogo vacío y filtros sin resultados.
El resumen utiliza la misma respuesta y no garantiza actualidad ni disponibilidad.

Búsqueda: pagina desde cero, tamanio=10 y ordenar=fechaHora,asc. No se envía el
texto al servidor. Cambiar ciudad/categoría o limpiar filtros vuelve a página 1;
el texto se conserva al avanzar. Las ciudades sugeridas provienen de páginas
consultadas y también se puede escribir una ciudad completa. No hay total de
páginas: Siguiente se habilita con 10 resultados y puede llegar a una página
vacía; Anterior permite volver. El filtro local de texto no decide si hay una
página siguiente. Un error no avanza automáticamente ni muestra resultados
viejos como si pertenecieran a la búsqueda nueva. Cada consulta cancela la
anterior; un contador ignora respuestas y errores antiguos aun si la cancelación
no es respetada por el transporte. La consulta conserva un timeout de 12 segundos.

Selección y carrito: /eventos/?id=ID carga GET /api/eventos/{id} y
GET /eventos/{id}/sectores. Esta página estática admite cualquier ID real sin
consultar al backend durante el build. Se retiraron las seis rutas antiguas
/eventos/1/…/eventos/6/ generadas con datos de ejemplo; usar el enlace del catálogo.
Las etiquetas de sector muestran su ID, ya que Disponibilidad no trae nombre.
Las filas/columnas se muestran como texto, sin generar IDs ni inventar un plano.
Cada selección contiene un solo sector: CAMPO usa cantidad y NUMERADO una lista
de IDs físicos. Cambiar sector limpia lo no guardado. El carrito admite tarjetas
separadas por evento/sector, sin checkout conjunto. Crear compra ahora envía POST /compras desde la revisión de selección.
Guardar requiere un perfil de demostración y no autentica, reserva ni cobra.

El carrito v2 usa localStorage separado por ID de perfil y no migra los borradores
de ejemplo anteriores. Guarda IDs, tipo, cantidad, asientos y fecha; no precios.
Al abrirlo y continuar una selección consulta de nuevo detalle y disponibilidad
del sector. Un fallo conserva el borrador como sin verificar. Si cambió cupo o
un asiento ya no está disponible, se avisa y se exige revisar/guardar nuevamente;
no se sustituye por otro asiento. También se revalida antes de guardar, y un
cambio de precio requiere otra revisión. El límite por cliente no está expuesto
por estas lecturas y será validado al comprar; 1000 es solo el máximo del DTO,
no un cupo garantizado. La UI bloquea estados/fechas no vendibles según su lectura,
pero la disponibilidad y el reloj local no reemplazan la autoridad del servidor.

Compra: el botón explícito crea un intento con clave crypto.randomUUID, perfil,
evento, sector, cantidad CAMPO o IDs NUMERADO y medioDePago=SIMULADO. Se persiste
clave y cuerpo en localStorage antes del POST, sin precios ni importes. No crea
reservas independientes. El intento queda bloqueado para cambios tras resultados
inciertos; Recuperar reutiliza el cuerpo exacto incluso tras recargar la página.
Con compra conocida se consulta GET /compras/{id}, luego GET /reservas/{idReserva}.
Se muestran ambos estados, vencimiento del servidor y contador orientativo local.
No se renueva la reserva. El pago requiere los botones explícitos de simulación. La consulta de estados es manual, sin polling.

Un 409 conserva el intento y actualiza disponibilidad; el usuario puede preparar
otro explícitamente tras revisar. Tras timeout/5xx/respuesta inválida no se permite
reemplazar la clave sin recuperar el resultado. Una compra PENDIENTE puede tener
reserva VENCIDA: se muestran por separado. Solo estados terminales consultados o
un conflicto confirmado habilitan preparar otro intento. Un fallo al consultar
reserva conserva los IDs conocidos. Almacenamiento inaccesible bloquea el POST.
La persistencia no es un bloqueo transaccional entre pestañas; evitar crear
compras simultáneas desde varias pestañas. No se probó contra el backend real.

Pago de demostración: aprobar/rechazar envía PUT /compras/{id}/pago?aprobado=true
o false, sin tarjeta ni cobro real. El resultado visible siempre usa estadoCompra
y distingue APROBADA, RECHAZADA y EXPIRADA incluso con HTTP 200. Se persiste la
decisión antes del PUT. Los controles comparten bloqueo durante solicitudes y
quedan deshabilitados al obtener un estado terminal. Tras timeout se conserva la
decisión: consultar por GET o reintentar manualmente solo la misma decisión.
GET /entradas/compra/{id} se ejecuta exclusivamente tras APROBADA. Si falla,
se conserva la aprobación y se permite recuperar entradas sin reenviar el pago.
Las entradas emitidas se muestran como lista de IDs y estados en la pantalla de
selección y también se pueden consultar desde la pestaña de entradas del perfil.

Historial: GET /compras/cliente/{idCliente} reemplaza las compras ficticias del
perfil. Mantiene pestaña, filtros por estados reales y modal de detalle. Importes,
sectores, cantidades, asientos, medios y referencias provienen de Compras. No se
inventa fecha de compra ni moneda; el DTO no las incluye. El título del evento se
resuelve por GET /api/eventos/{id}; si falla se conserva Evento #ID. Para PENDIENTE
se consulta GET /reservas/{idReserva}; se muestran estado y vencimiento separados.
Solo una reserva ACTIVA y no vencida según la lectura habilita Retomar pago
simulado. Sin verificar, vencida o liberada no se ofrece como pagable.

Retomar navega a /eventos/?id=ID&compra=ID#purchase-panel y vuelve a consultar
GET /compras/{id} y su reserva antes de habilitar los botones. No crea otra compra
ni reserva, respeta el perfil y conserva cualquier decisión de pago local. No
sobrescribe otro intento incierto del mismo evento. Volver a mis compras conserva
la navegación del perfil; al regresar se actualiza el historial. Estados y títulos
tardíos de otra carga/perfil no reemplazan los actuales.

Entradas y QR: ver PERFIL.md. La vista del cliente solo realiza lecturas y no
consume entradas. Se eliminó la dependencia de datos/estados/QR de ejemplo del
flujo integrado. La generación QR usa exactamente la cadena de GET /entradas/{id}.
Las devoluciones se envían al servidor y no se inventa un acceso válido
tras rechazo de devolución. Eventos o ubicaciones inaccesibles aparecen por ID,
sin sustituirse por datos ficticios. Las respuestas atrasadas no muestran QR de
otra entrada o perfil. Mostrar un QR no equivale a una validación de ingreso.

Devoluciones integradas: ver PERFIL.md. GET /solicitudes/cliente/{id} muestra el
historial real. POST /solicitudes solicita por entrada con motivo obligatorio,
plazo de 72 horas y autoridad final del servidor. Se recargan solicitudes y
entradas después del envío y se conservan los resultados inciertos sin reintentos
automáticos. La marca local evita repetir desde este navegador, pero no reemplaza
idempotencia o coordinación entre dispositivos. Si el servidor no permite saber
si un envío incierto creó una solicitud, la UI mantiene el bloqueo para evitar
duplicados y requiere reconciliar el estado. Las devoluciones rechazadas mantienen
la limitación documentada de uso QR; no se anuncia acceso válido.

Preferencias y artistas favoritos: ver PERFIL.md. Mis datos permite reemplazar
tipos y ciudad preferida, siempre con ambos campos completos. Artistas se eligen
desde el catálogo y se agregan/quitan por ID en el backend; los IDs sin datos
resueltos siguen siendo eliminables. Se distinguen explícitamente de los eventos
guardados solo en este navegador. Hay carga, vacío, errores y recarga; no se
reemplaza una edición pendiente al actualizar favoritos.

## Recomendaciones

La pestaña Para vos (/perfil/#recomendaciones) y el enlace de Mi cuenta consultan
GET /usuarios/{id}/recomendaciones para el perfil seleccionado. No se consultan
rutas internas ni fuentes individuales. Se conserva el orden del servidor y se
muestran motivos, fuentes, fecha y ciudad, con imagen ilustrativa local. El enlace
al evento vuelve a consultar su detalle y disponibilidad al preparar la selección.
No se inventan precios, ventas o cupos.

Se muestran por separado parcial, fuentesNoDisponibles, historialVerificado y
disponibilidadVerificada. Un historial no verificado puede incluir eventos ya
comprados; disponibilidad no verificada significa cupo sin comprobar. Incluso si
la disponibilidad fue verificada, puede cambiar: recomendar no garantiza cupo.
Una lista vacía válida no se trata como error; si hay limitaciones se advierte que
no equivale a ausencia de opciones. 503 u otros errores muestran reintento sin
sustituir recomendaciones por ejemplos. Flags ausentes o malformados no se
interpretan como verificaciones exitosas. Respuestas y errores antiguos se
descartan, también al cambiar de perfil. Guardar preferencias o modificar
artistas favoritos actualiza las recomendaciones; también hay actualización manual.
La selección de perfil continúa siendo una demostración, sin autenticación.
