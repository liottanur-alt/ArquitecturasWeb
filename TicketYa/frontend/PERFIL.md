# Perfil, historial y carrito

`/perfil/` requiere iniciar sesión. Redirige al login conservando la pestaña
y los parámetros de la URL. Las consultas cargan el perfil y los datos de compra
usando el ID de la cuenta autenticada. La edición de perfil conserva el `idRol`
que devuelve el servidor. Los detalles del ingreso están en [Inicio de sesión y registro](LOGIN.md).

El historial usa GET /compras/cliente/{idCliente}; no contiene compras ficticias.
Los filtros corresponden a PENDIENTE/APROBADA/RECHAZADA/EXPIRADA/CANCELADA.
Los pendientes enlazan al detalle de la compra para consultar el estado vigente
y retomar el flujo. El modal muestra importes y referencias del servidor. La
pestaña de entradas también consulta el Gateway.

El carrito se guarda localmente por cuenta. Guarda un borrador por
evento y sector con IDs reales, cantidad CAMPO o asientos NUMERADO; no precios.
La versión v2 no importa el carrito de ejemplo. Para recuperar cada tarjeta se
consulta el evento publicado y la disponibilidad actual del sector. Los fallos
se muestran como sin verificar. Los cambios requieren revisar la selección.
Continuar abre /eventos/?id=ID&sector=ID&carrito=1 y vuelve a revalidar. Se puede
quitar una tarjeta sin borrar las de otros sectores o perfiles. Guardar no reserva
lugares ni crea una compra. No hay un total global que mezcle verificaciones.

## Mis entradas

La pestaña usa GET /entradas/cliente/{idCliente}. Ver entradas desde una compra
consulta primero GET /compras/{id} para verificar el perfil y luego
GET /entradas/compra/{idCompra}. Se conserva el filtro de compra y se puede volver
a todas las entradas. También admite /perfil/?compra=ID#entradas desde el pago.
Muestra estados de entrada y QR, importe, evento, sector y asiento. Los nombres
se resuelven por el Gateway. Ante un fallo se conservan los IDs reales.
Antes de mostrar un QR consulta GET /entradas/{id} y genera la imagen con la cadena
exacta qr, sin prefijos ni transformaciones. Solo PAGA y NO_ESCANEADO habilita QR;
DEVOLUCION_RECHAZADA sigue la limitación de acceso del backend y no se trata como
PAGA. No se llama a PATCH /uso ni se cambia localmente el estado de escaneo.
Mostrar el QR no garantiza ingreso. Las devoluciones se consultan y envían al
servidor; no se modifican estados de entrada ni escaneo localmente. Hay carga,
error, reintento, vacío y filtros por estado.

La edición valida obligatorios, email y límites del mismo contrato que el alta.
PUT conserva el idRol del perfil leído, sin ofrecer selector de roles. Durante
GET/PUT los campos y el reintento quedan deshabilitados y se muestra carga.
Un error de guardado conserva lo escrito; 409 indica posible email/DNI duplicado
o conflicto concurrente. Los mensajes del servidor se muestran como texto.

## Devoluciones

Mis solicitudes consulta GET /solicitudes/cliente/{idCliente} y distingue PENDIENTE,
ACEPTADA, RECHAZADA y CANCELADA. Solicitar devolución actúa sobre una sola entrada:
reconsulta entrada, historial de solicitudes y fecha pública del evento. El plazo
es hasta 72 horas antes del inicio, inclusive, interpretado en Buenos Aires.
Si no se puede leer la fecha se indica y se deja validar al servidor, porque un
evento suspendido puede seguir admitiendo solicitudes aunque el detalle no sea
público. Un rechazo anterior permite otra solicitud si cumple las reglas, pero
no convierte la entrada en PAGA ni vuelve a habilitar el QR.

POST /solicitudes envía idEntrada, idCliente y descripcion no vacía. Se bloquean
envíos simultáneos y se registra una marca local por perfil/entrada antes de enviar.
No es una clave de idempotencia del servidor. Ante timeout, 5xx o respuesta inválida
no se reenvía automáticamente ni se asume fracaso; se consulta el historial. Si
no aparece una nueva solicitud, el envío permanece bloqueado para evitar un
duplicado incierto. Errores 400/403/404/409 muestran el mensaje real y recargan
las lecturas. Tras solicitar se consultan otra vez solicitudes y entradas.
PENDIENTE no confirma aceptación ni reintegro; los reintegros son simulados y los
estados se muestran tal como los devuelve el servidor. No se resuelven solicitudes
desde la vista del cliente. No hay notificaciones push; se puede actualizar.

## Preferencias y artistas favoritos

En Mis datos se editan tipos y ciudad preferida mediante GET/PUT
/usuarios/{id}/preferencias. Cada PUT envía tiposEventoPreferidos completo y
ciudadPreferida (texto trim o null). Sin tipos y con ciudad vacía se vacían ambos
campos. Nunca se deriva la ciudad preferida de la ciudad del perfil. PUT devuelve
Usuario; la UI valida el ID y lee de allí los campos normalizados.

GET /api/artistas alimenta el selector. GET /usuarios/{id}/artistas-favoritos
resuelve los nombres con idArtista, a diferencia del id del catálogo. Los IDs
guardados de GET preferencias permiten quitar favoritos aunque un artista borrado
haga fallar la resolución de nombres. PUT/DELETE /usuarios/{id}/artistas-favoritos/
{idArtista} se ejecuta sin cuerpo, admite 204 y después reconsulta IDs. Ante un
fallo de verificación se deshabilitan las mutaciones hasta recargar. Una lectura
de favoritos no modifica una edición de preferencias pendiente. No hay reintentos
automáticos de escrituras. El backend no valida existencia al agregar un favorito;
la UI solo ofrece altas desde el catálogo recibido.

Artistas favoritos son datos del servidor asociados al perfil. Los corazones del
catálogo guardan eventos en el navegador, separados por cuenta. La lista local
anterior, compartida entre cuentas, queda fuera de las listas individuales.

## Recomendaciones

La pestaña Para vos (/perfil/#recomendaciones) y el enlace de Mi cuenta consultan
GET /usuarios/{id}/recomendaciones para la cuenta autenticada. No se consultan
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
