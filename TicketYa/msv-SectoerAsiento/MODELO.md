# Modelo de sectores, asientos y reservas

- Sector representa un sector fisico de un lugar. idLugar es una referencia al servicio de eventos; no existe una relacion JPA entre microservicios. capacidadTotal es su limite fisico.
- Asiento representa la posicion fisica (fila y columna) dentro de un sector numerado. Esa posicion es unica por sector.
- SectorEvento configura un sector para un evento: precio con BigDecimal y capacidadHabilitada. La pareja evento-sector es unica. En CAMPO, lugaresReservados y lugaresVendidos permiten calcular el cupo disponible.
- AsientoEvento representa el estado DISPONIBLE, RESERVADO o VENDIDO de un asiento para una configuracion SectorEvento. La pareja configuracion-asiento es unica.
- Reserva pertenece a un cliente y a un SectorEvento. Guarda cantidad, precioUnitario al reservar, creacion, vencimiento, estado y claveIdempotencia unica. Para NUMERADO contiene los AsientoEvento reservados; para CAMPO la lista esta vacia y se utiliza cantidad. La tabla reservas_asientos conserva la asociacion historica, por eso un asiento puede aparecer en reservas anteriores liberadas o vencidas.

No se utilizan cascadas de borrado: borrar configuraciones fisicas no debe eliminar reservas o ventas historicas. Los estados iniciales son DISPONIBLE para AsientoEvento y ACTIVA para Reserva. TipoSector.SECTOR se reemplazo por NUMERADO.

## Reglas a implementar en la capa de servicio

Estas entidades preparan el modelo; no implementan por si solas el circuito de compra ni garantizan la ausencia de doble venta.

- Validar que la capacidad habilitada no supere la capacidad fisica. Un sector CAMPO no tiene asientos individuales.
- Validar que cada AsientoEvento pertenezca al mismo sector de su SectorEvento. La configuracion del evento debe corresponder al lugar del sector.
- Para una reserva numerada, cantidad debe coincidir con la cantidad de asientos distintos y todos deben pertenecer al mismo SectorEvento. Para campo, la lista debe estar vacia.
- Reservar, confirmar y liberar en transacciones. Bloquear las filas de disponibilidad (SectorEvento para campo y AsientoEvento para numerados) o manejar explicitamente los conflictos de version. @Version permite detectar escrituras concurrentes, pero requiere tratar el error y repetir o rechazar la operacion completa.
- Cambiar estado, cupos y reserva en la misma transaccion. En numerados, consultar disponibilidad desde AsientoEvento; no mantener un segundo conteo independiente.
- Verificar que la reserva siga activa y no haya vencido al confirmar. Una tarea periodica debera liberar reservas vencidas. No borrar sus asociaciones historicas.
- La clave de idempotencia identifica una solicitud de reserva: un reintento debe recuperar la existente y verificar que el contenido coincida. Confirmar o liberar repetidamente debe ser seguro.
- Los CHECK de base de datos impiden capacidades o cantidades invalidas, importes negativos y vencimientos anteriores a la creacion. Requieren una base que aplique restricciones CHECK y la estrategia de nombres predeterminada de Spring Boot.

## Cambio de esquema

El modelo anterior almacenaba precio y disponibilidad en Sector y evento/estado en Asiento. Esos datos ahora pertenecen a SectorEvento y AsientoEvento. Si ya existen tablas o datos, se necesita una migracion antes de utilizar el modelo nuevo; no alcanza con compilar ni con actualizar automaticamente el esquema.

## API de sectores

El controlador gestiona sectores fisicos; precios y cupos por evento corresponden a SectorEvento.

| Metodo | Ruta | Respuesta |
| --- | --- | --- |
| POST | /sectores | 201 con Location |
| GET | /sectores | 200, lista; filtro opcional ?idLugar=1 |
| GET | /sectores/{id} | 200 o 404 |
| PUT | /sectores/{id} | 200, 404 o 409 si tiene asientos/eventos asociados |
| DELETE | /sectores/{id} | 204, 404 o 409 si tiene asientos/eventos asociados |

Body para POST y PUT:

```json
{"idLugar":1,"nombre":"Campo","tipo":"CAMPO","capacidadTotal":100}
```

Los tipos admitidos son CAMPO y NUMERADO. IDs y capacidad deben ser positivos, nombre obligatorio (maximo 255 caracteres). Solicitudes invalidas devuelven 400. Los errores usan ProblemDetail. Se almacena idLugar como referencia externa; todavia no se valida su existencia contra el servicio de eventos.

La edicion se bloquea por completo cuando hay referencias, como politica inicial conservadora. La configuracion de datasource sigue pendiente en application.properties para ejecutar con MySQL. Pruebas del controlador y servicio: `mvn -Dtest=SectorControllerTests test` (repositorios simulados, sin base de datos).

## API de asientos

- POST /asientos: crea, devuelve 201 y Location.
- GET /asientos: lista, admite ?idSector=1.
- GET /asientos/{id}: consulta, devuelve 200 o 404.
- PUT /asientos/{id}: actualiza fila y columna dentro del mismo sector.
- DELETE /asientos/{id}: elimina, devuelve 204.

Body POST/PUT:

```json
{"idSector":1,"fila":"A","columna":"1"}
```

El sector debe existir (404) y ser NUMERADO (409 para CAMPO). La posicion debe ser unica y no se puede superar capacidadTotal. Se recortan espacios de fila y columna. Datos faltantes, IDs no positivos o textos vacios devuelven 400; conflictos de posicion/capacidad devuelven 409. No se permite trasladar asientos ni alterar la distribucion de sectores configurados para eventos. Asientos asociados a eventos no se pueden modificar o borrar.

La creacion bloquea la fila del sector mientras cuenta e inserta, serializando altas concurrentes sobre el mismo sector. La restriccion unica de base de datos protege posiciones duplicadas. Este bloqueo no reemplaza el futuro control de reservas/ventas sobre AsientoEvento. La configuracion de sectores para eventos debera usar el mismo bloqueo para coordinar cambios de distribucion.

Pruebas HTTP con repositorios simulados: `mvn -Dtest=SectorControllerTests,AsientoControllerTests test`. No verifican bloqueos reales en MySQL.

## Clientes Feign: contratos provisionales

Se agregan EventoClientFeign y CompraClientFeign, habilitados por FeignConfig. Se utiliza el mismo BOM Spring Cloud 2025.1.4 que msv-usuario. Las rutas de ambos proveedores aun no estan implementadas: estas interfaces son una propuesta para acordar entre integrantes, no una integracion funcional confirmada.

| Cliente | Metodo Java | HTTP propuesto |
| --- | --- | --- |
| EventoClientFeign | obtenerEvento(id) | GET /eventos/{id} |
| EventoClientFeign | obtenerLugar(id) | GET /lugares/{id} |
| CompraClientFeign | obtenerCompra(id) | GET /compras/{id} |
| CompraClientFeign | obtenerEntrada(id) | GET /entradas/{id} |

Los DTOs de client/dto usan IDs externos planos, no entidades JPA remotas. EventoResponse espera idLugar (el proveedor debera mapear su relacion lugar). CompraResponse representa id, idCliente, idEvento, medioDePago, descripcion y cantidadEntradas del diagrama. EntradaResponse representa qr, estadoQr, IDs de cliente/evento/sector/asiento, precio y estadoEntrada. idAsiento es null para campo; precio usa BigDecimal. Los nombres JSON esperados son exactamente los de los componentes de los records; no se espera un objeto envolvente. Los estados remotos se reciben como String hasta acordar sus valores definitivos.

URLs configurables: MSVC_EVENTOS_URL (valor provisional http://localhost:8001), MSVC_COMPRAS_URL (http://localhost:8003). Timeout de conexion: 3 segundos; lectura: 5 segundos para ambos clientes. Se conserva el comportamiento por defecto de OpenFeign sin reintentos automaticos. Los errores HTTP se propagan como FeignException; antes de incorporar llamadas a endpoints publicos se deberan traducir 404, indisponibilidad y timeouts, sin devolver respuestas exitosas ficticias.

Estos clientes se pueden inyectar por constructor en futuros servicios. No se invocan desde el CRUD actual, porque los proveedores no tienen endpoints implementados. Compras debera orquestar reserva, pago y confirmacion/liberacion; estos clientes de consulta no implementan ese flujo ni el de devoluciones. No se modifican los proyectos proveedores ni las rutas del gateway.

Pruebas: FeignClientsTests verifica registro de proxies y contratos de rutas/parametros sin contactar servicios externos; no reemplaza una prueba de integracion con los proveedores.

## Servicios de estructura fisica

SectorService y AsientoService tienen Javadoc por metodo: finalidad, reglas y errores. @Validated y @Valid validan tambien invocaciones desde otros beans de Spring, antes de acceder a los repositorios. Las consultas admiten filtro opcional; escrituras requieren IDs positivos y DTOs validos. Se mantienen la politica conservadora de referencias y las transacciones del CRUD.

Al editar o eliminar asientos, primero se consulta solo el ID del sector y se bloquea ese sector; despues se carga el asiento. De esta manera no se carga su estado editable antes de esperar el bloqueo. La pertenencia a sector es inmutable. Las altas, cambios y bajas utilizan el mismo bloqueo que las modificaciones de SectorService.

Alcance: estos servicios administran sectores y asientos fisicos. La configuracion SectorEvento, la disponibilidad AsientoEvento y las reservas/ventas requieren servicios propios; el CRUD no implementa ese circuito. Feign sigue siendo un contrato provisional y no valida referencias externas en estas operaciones.

## Circuito de inventario implementado

Las secciones anteriores describen etapas previas. El estado vigente, endpoints y pruebas estan en README.md y static/openapi.json. InventarioEvento coordina cada evento, Reserva incluye cantidadDevuelta y asientosDevueltos, y Devolucion conserva claves de reintentos y la fecha original de solicitud. Se agregaron configuracion por evento, reservas temporales, confirmacion, liberacion, vencimiento, limite por cliente, devoluciones parciales y cancelacion. La validacion Feign de Eventos ahora se utiliza al configurar, reservar, confirmar y devolver. El reintegro monetario sigue siendo responsabilidad de Compras.

## Ajuste al contrato de main

Eventos ya tiene controladores: usa 8005, /api/eventos/{id} y lugar anidado. Feign y su DTO se adaptaron a ese formato. MySQL de Sectores se publica ahora en 3310 para no colisionar con MySQL de Eventos en 3308. Las notas anteriores sobre rutas provisionales de Eventos quedan reemplazadas por esta configuracion.
