# Compras, entradas y solicitudes

Microservicio de compra de TicketYa. Orquesta la reserva de lugares en SectorAsiento, el pago simulado, la emisión de entradas con QR, las devoluciones/reintegros y la cancelación de eventos. Cubre las historias HU-08 a HU-14.

- Puerto HTTP: **8003**
- Base de datos: MySQL **3306**, esquema `compra_entrada_sol`
- Dependencias: Eventos (`http://localhost:8005`) y SectorAsiento (`http://localhost:8002`)
- No emite dinero real: el pago y los reintegros son simulados y siempre terminan en `COMPLETADO`.

## Ejecucion local

Requiere Java 21 o superior, Docker Desktop y conexion a internet la primera vez.

```powershell
docker compose -f ../docker-compose.yml up -d --wait mysql
.\mvnw.cmd spring-boot:run
```

El MySQL compartido se publica en `127.0.0.1:3306`. El Compose raiz usa un solo MySQL con cuatro esquemas y crea compra_entrada_sol; iniciarlo con docker compose -f ../docker-compose.yml up -d --wait mysql. Antes de iniciar el servicio deben estar arriba Eventos y SectorAsiento si se van a probar compras reales.

Variables de entorno:

| Variable | Valor por defecto |
| --- | --- |
| `SERVER_PORT` | `8003` |
| `DB_URL` | `jdbc:mysql://localhost:3306/compra_entrada_sol` |
| `DB_USERNAME` | `compra_app` |
| `DB_PASSWORD` | `compra_dev` |
| `DDL_AUTO` | `update` |
| `MSVC_EVENTOS_URL` | `http://localhost:8005` |
| `MSVC_SECTORES_URL` | `http://localhost:8002` |

`DDL_AUTO=update` crea/actualiza el esquema de desarrollo. Para una instalacion con datos previos se recomienda respaldo y una migracion revisada.

## Flujo de compra

1. `POST /compras` con `claveIdempotencia`, cliente, evento, sector y asientos (o cantidad para sectores sin numerar).
2. El servicio valida el evento en Eventos y crea la reserva en SectorAsiento.
3. `PUT /compras/{id}/pago?aprobado=true` confirma la reserva y emite una entrada por lugar con QR unico.
4. `PUT /compras/{id}/pago?aprobado=false` libera la reserva y deja la compra `RECHAZADA`.
5. Si la reserva vencio antes de confirmar, la compra queda `EXPIRADA` y el pago simulado se reintegra.

Todas las operaciones son idempotentes por `claveIdempotencia` o por estado final. Reintentar el mismo pedido devuelve la compra original; reutilizar la clave con otro contenido devuelve `409`.

## Devoluciones

1. `POST /solicitudes` registra una solicitud `PENDIENTE` si faltan al menos 72 horas para el evento.
2. Registrar la solicitud **no** libera el lugar ni reintegra dinero.
3. `PUT /solicitudes/{id}/resolver` con `ACEPTADA` libera el asiento en SectorAsiento y ejecuta el reintegro simulado.
4. Con `RECHAZADA`, la entrada conserva su validez.
5. Repetir la misma aprobacion no vuelve a liberar ni a reintegrar.

## Cancelacion de evento

`POST /compras/eventos/{idEvento}/cancelacion`:

- marca primero el evento como `CANCELADO` en Eventos para bloquear nuevas ventas;
- cancela compras `PENDIENTE` liberando su reserva;
- reintegra las entradas vigentes de compras `APROBADA` sin generar un segundo reintegro para entradas ya devueltas;
- cancela solicitudes pendientes asociadas;
- invoca `POST /eventos/{idEvento}/inventario/cancelacion` de SectorAsiento (una sola llamada desde Compras);
- es idempotente: repetirla completa cualquier paso pendiente sin duplicar reintegros.

Esta es la operacion que se debe usar para cancelar un evento completo. Si un servicio falla despues de marcar el evento, se puede repetir la misma solicitud; el evento, los reintegros y el inventario toleran el reintento.

## API y errores

OpenAPI 3.0 en `src/main/resources/static/openapi.json`, servido en <http://localhost:8003/openapi.json>. El contrato detallado esta en [REST.md](REST.md).

| Codigo | Significado |
| --- | --- |
| 400 | Datos invalidos o pedido incompleto |
| 403 | La entrada no pertenece al cliente |
| 404 | Compra, entrada, solicitud o evento inexistente |
| 409 | Transicion invalida, clave de idempotencia reutilizada, devolucion duplicada o limite |
| 502 | Un servicio requerido devolvio un error |
| 503 | No se pudo conectar con Eventos o SectorAsiento |

Todas las respuestas de error comparten el formato `{error, detalle, correlationId, timestamp}`. El header `X-Correlation-Id` se propaga a las llamadas Feign.

## Pruebas

```powershell
.\mvnw.cmd test
```

La suite usa H2 en modo MySQL y reemplaza los clientes Feign con `@MockitoBean`. Incluye:

- `CompraServiceTests`: compra, idempotencia, pago aprobado/rechazado/vencido, cancelacion y doble reintegro.
- `EntradaServiceTests`: emision, consulta y marcado de uso del QR.
- `SolicitudServiceTests`: regla de 72 horas, permisos, aprobacion, rechazo e idempotencia.
- `ControllerTests`: validacion HTTP, mapeo de errores y contrato JSON.
- `FeignContractTests`: nombres de campos reales de Eventos y SectorAsiento.

La guia manual con Postman esta en [docs/PRUEBAS-POSTMAN.md](docs/PRUEBAS-POSTMAN.md).

## Notas de integracion

- SectorAsiento exige que `solicitadaEn` sea posterior a la reserva, no futura y anterior a las 72 horas previas al evento. Compras envia la fecha original de la solicitud.
- El unique `(id_evento, id_asiento)` de `entrada` se elimino para permitir la reventa de un asiento despues de una devolucion. Si la base ya existia, ejecutar una vez:

  ```sql
  ALTER TABLE entrada DROP INDEX uk_entrada_evento_asiento;
  ```

- El gateway ya expone `/compras`, `/entradas` y `/solicitudes` hacia el puerto 8003 (`MSVC_COMPRAS_URL`). Tambien se puede llamar directo al 8003.
- No hay autenticacion ni roles en esta entrega.
- El pago y los reintegros son simulados. Las notificaciones a los compradores afectados por una cancelacion quedan para la Entrega 2, igual que la proteccion por roles de los endpoints administrativos.
