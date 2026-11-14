# SectorAsiento

Gestiona sectores/asientos fisicos y su inventario por evento: precio, capacidad, reservas de diez minutos, confirmacion, liberacion, vencimientos, devoluciones parciales y cancelacion. Cada metodo nuevo tiene un comentario breve. `InventarioService` concentra las transacciones y `VencimientoReservas` ejecuta el barrido.

## Ejecucion local

Requiere Java 21 o superior, Maven y Docker Desktop para MySQL. Desde esta carpeta:

```powershell
docker compose -f compose.mysql.yml up -d --wait
$env:MSVC_EVENTOS_URL='http://localhost:8005'
.\mvnw.cmd spring-boot:run
```

El servicio escucha en 8002. MySQL se publica en 3306 y comparte el volumen ticketya_mysql_data con las bases de Usuarios y Eventos; cada servicio mantiene su propio esquema y usuario. La URL, usuario, password y puerto del servicio pueden cambiarse con DB_URL, DB_USERNAME, SECTOR_DB_PASSWORD (o DB_PASSWORD) y SERVER_PORT. `DDL_AUTO=update` crea el esquema de desarrollo sin borrar datos; no migra de forma segura instalaciones con las entidades antiguas. Antes de reutilizar una base anterior se requiere respaldo y una migracion revisada; usar una base nueva para este modelo.

El proveedor Eventos debe exponer `GET /api/eventos/{id}` con `id`, `lugar.id`, `fechaHora`, `estado` y `estadoPublicacion`. `fechaHora` es hora local en America/Argentina/Buenos_Aires; configurar inventario.zona-eventos si el contrato cambia. Solo se reservan/confirman lugares de eventos PUBLICADO, no cancelados, agotados, finalizados o iniciados. El cliente se adapto al contrato de Eventos incorporado desde main; probar ambos servicios reales con MySQL usando la guia de pruebas de la raiz. Las pruebas levantan un servidor HTTP local que responde ese contrato y ejercitan Feign realmente.

El gateway ahora enruta `/sectores`, `/asientos`, `/reservas` y las subrutas de inventario/sectores de eventos. Ejecutar el gateway en su propio proyecto; `MSVC_SECTORES_URL` tiene como valor por defecto http://localhost:8002. La ruta general `/eventos/{id}` sigue perteneciendo al catalogo y no se captura aqui.

## Flujo para probar

Importar `TicketYa-SectorAsiento.postman_collection.json` en Postman. Definir idEvento con un evento futuro publicado, cuyo lugar tenga ID 1, o adaptar el request de sector. Para una ejecucion nueva, cambiar las claves de reserva/devolucion y limpiar solicitadaEn. La coleccion captura IDs generados.

1. Crear Sector NUMERADO y todos sus Asientos. Para CAMPO no crear asientos.
2. PUT `/eventos/{idEvento}/inventario` con `{"limitePorCliente":4}`. Consulta Eventos.
3. PUT `/eventos/{idEvento}/sectores` con `{"idSector":1,"precio":1500,"capacidadHabilitada":1}`. El sector debe pertenecer al mismo lugar; en numerados la capacidad coincide con la cantidad de asientos fisicos.
4. GET `/eventos/{idEvento}/sectores` o `/eventos/{idEvento}/sectores/{idSector}/disponibilidad`.
5. POST `/reservas` con claveIdempotencia, idCliente, idEvento, idSector, cantidad e idAsientos. Los IDs son de asientos fisicos; CAMPO envia []. Devuelve 200 tanto al crear como al recuperar la misma solicitud.
6. Tras pago aprobado: POST `/reservas/{id}/confirmacion`. Emitir entradas solamente si el estado devuelto es CONFIRMADA. Si devuelve VENCIDA, Compras debe compensar el pago tardio y no emitir QR.
7. Si falla el pago: POST `/reservas/{id}/liberacion`. Se puede repetir sin liberar de nuevo. No sirve para deshacer una venta confirmada.
8. Devolucion aprobada: POST `/reservas/{id}/devoluciones` con claveIdempotencia, cantidad, solicitadaEn ISO UTC e idAsientos. Permite devolucion parcial. Compras informa la fecha original de la solicitud; se valida que no sea futura, anterior a la reserva ni posterior a las 72 horas previas al evento. La aprobacion puede llegar despues. Repetir exactamente el mismo pedido es seguro.
9. Cancelacion: POST `/eventos/{idEvento}/inventario/cancelacion`. Libera ventas/reservas pendientes y bloquea nuevas compras. La operacion es idempotente. Compras debe procesar reintegros y notificaciones por su cuenta.

No se eliminan reservas historicas. La clave de reserva y la clave de devolucion son unicas globalmente dentro de su tipo. Reutilizarlas con otro contenido devuelve 409. Las devoluciones numeradas conservan los asientos ya devueltos para que un reintento no libere una reventa ajena.

## Concurrencia y consistencia

Todas las operaciones de inventario adquieren un bloqueo PESSIMISTIC_WRITE de la cabecera del evento en la base de datos, con aislamiento READ_COMMITTED. El bloqueo serializa cambios de todos sus sectores entre distintas instancias del servicio y permite aplicar el limite por cliente en el evento entero. Reserva, cupos y estados se escriben en una transaccion local; un error revierte todo. Unicidad y @Version son protecciones adicionales. Se privilegia la correccion: este bloqueo por evento limita el paralelismo y debe revisarse con la prueba de carga de la entrega 2.

No hay una transaccion distribuida con pagos. Compras orquesta: reservar -> pagar -> confirmar. Ante rechazo libera; ante pago aprobado y confirmacion fallida reconsulta la reserva con el mismo ID; si no puede confirmar debe compensar el pago. El cliente Feign de Compras sigue siendo provisional: este servicio no cobra ni emite QR.

El vencimiento se procesa al consultar/operar y por una tarea periodica de 30 segundos, coordinada con el mismo bloqueo. Confirmar una reserva vencida devuelve VENCIDA con 200 para que la liberacion se confirme en la base; no lanza una excepcion que revierta ese cambio. La disponibilidad HTTP usa Cache-Control: no-store.

## API y errores

OpenAPI 3.0 en `src/main/resources/static/openapi.json`, servido en http://localhost:8002/openapi.json. Se puede importar en Swagger Editor o Postman. Incluye las rutas, DTOs y ejemplos. No incluye autenticacion: corresponde a la entrega 1. Los endpoints internos de confirmacion, devolucion y cancelacion deben protegerse por roles/servicios en la entrega 2.

400: datos invalidos. 404: recurso inexistente. 409: capacidad, duplicados, limite o transicion invalida. 502: contrato remoto incompleto. 503: proveedor no disponible o bloqueo ocupado; reintentar usando la misma clave. No se inventan respuestas exitosas ante fallos de red. Feign tiene timeouts de 3/5 segundos y no reintenta automaticamente.

## Pruebas

La comunicación con Eventos usa Feign con Apache HttpClient 5 (`feign-hc5`), que permite las llamadas PATCH de sincronización del agotamiento. El proveedor HTTP de pruebas contempla las rutas internas actuales de Eventos. Verificación del 14/11/2026: 37 pruebas correctas con H2 y proveedor HTTP local; la prueba conjunta con MySQL sigue pendiente.

```powershell
.\mvnw.cmd test
```

Pruebas unitarias/HTTP, validacion, arranque Spring, contrato Feign y transacciones reales en H2. Las pruebas de concurrencia lanzan doce transacciones por el mismo asiento o ultimo cupo de campo y verifican una unica venta. Otra prueba reutiliza la misma clave concurrentemente y verifica una sola reserva/venta. Tambien se prueban devoluciones parciales, reventa, limite entre sectores, vencimientos y cancelaciones.

Para comprobar la misma suite contra MySQL, con Docker iniciado, usar exclusivamente la base descartable siguiente (create-drop elimina el esquema de pruebas):

```powershell
docker compose -f compose.mysql-test.yml up -d --wait
.\mvnw.cmd '-Dspring.datasource.url=jdbc:mysql://localhost:3309/inventario_test' '-Dspring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver' '-Dspring.datasource.username=test_app' '-Dspring.datasource.password=test_app' test
docker compose -f compose.mysql-test.yml down
```

Nunca apuntar esta suite a una base con datos a conservar. Las pruebas H2 verifican persistencia y concurrencia en esa base; no sustituyen la verificacion especifica de MySQL ni una prueba de carga con varias JVM.

## Documentacion de diseno

`docs/arquitectura.md` contiene el diagrama de modulos, las relaciones locales y la secuencia de compra con rechazo/vencimiento. `MODELO.md` conserva el historial de decisiones; este README describe el contrato actual.

## Verificacion ejecutada

Se ejecutaron 37 pruebas del microservicio y 6 del gateway, todas correctas. Las pruebas de persistencia y concurrencia utilizaron H2 y un proveedor HTTP de Eventos local. No se pudo ejecutar MySQL: Docker Desktop no estaba iniciado. OpenAPI y la coleccion Postman se verificaron como JSON y se revisaron sus referencias internas.

## Limites del entregable

Esta implementacion completa el circuito de inventario del microservicio para la entrega 1. La compra completa, pasarela mock, QR, aprobacion administrativa y reintegros monetarios pertenecen a otros microservicios. Queda pendiente conectar los proveedores reales y ejecutar la suite MySQL cuando Docker este disponible. JWT, fila virtual, NoSQL, reportes, recomendaciones, log centralizado y el despliegue Docker del sistema completo corresponden a la entrega 2 y al equipo, no a este CRUD.

## MySQL compartido

Desde la raíz del repositorio, `docker compose up -d --wait mysql` levanta las tres bases en un solo contenedor. El Compose de esta carpeta incluye el mismo proyecto por compatibilidad. La migración de datos anteriores y la verificación de permisos están en [../docker/mysql/README.md](../docker/mysql/README.md).
