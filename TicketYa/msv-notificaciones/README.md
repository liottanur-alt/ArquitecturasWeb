# msv-notificaciones

Microservicio de TicketYa para notificaciones, reportes y bitácora.

## Ejecutar localmente

Requiere Java 21 y Maven. Primero levantar MySQL desde la carpeta raíz de TicketYa:

```powershell
docker compose -f ./msv-notificaciones/docker-compose.yml up -d --wait
```

Después ejecutar la aplicación desde `msv-notificaciones`:

```powershell
.\mvnw.cmd spring-boot:run
```

El servicio usa el puerto `8006` y la base `notificaciones` en MySQL, puerto local `3309`. Configurá el mismo `JWT_SECRET` que usa Gateway antes de iniciarlo. La configuración se puede cambiar con `SERVER_PORT`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `DDL_AUTO`.

## Registrar sucesos

Los módulos publican internamente en `POST /api/sucesos` después del commit. La `claveIdempotencia` se reutiliza en reintentos para no duplicar el suceso. Consultá [el contrato de la bandeja y los productores](../DOCS_NOTIFICACIONES.md).

```json
{
  "claveIdempotencia": "compra-confirmada-123",
  "tipo": "COMPRA_CONFIRMADA",
  "idEvento": 45,
  "idCompra": 123,
  "idCliente": 9,
  "destinatario": "cliente@example.com",
  "cantidadEntradas": 2,
  "importe": 25000.00,
  "capacidadTotal": 500,
  "fechaSuceso": "2026-11-17T12:30:00Z"
}
```

En compras confirmadas y devoluciones aprobadas se deben incluir `cantidadEntradas` e `importe` para que los reportes puedan calcularse. `capacidadTotal` permite estimar la ocupación.

## Endpoints

- `POST /api/sucesos`: registra el suceso y lo agrega a la bitácora. Responde `201`; si la clave ya existe, devuelve el registro existente.
- `GET /api/reportes/eventos/{idEvento}`: devuelve ventas, devoluciones, ingresos netos y ocupación estimada.
- `GET /api/notificaciones`: devuelve la bandeja y el contador de avisos sin leer del titular del JWT.
- `PATCH /api/notificaciones/{id}/lectura` y `PATCH /api/notificaciones/lectura`: actualizan la lectura del aviso o de toda la bandeja.
- `GET /api/notificaciones/clientes/{idCliente}`: devuelve las últimas 100 notificaciones al titular.
- `GET /api/bitacora?idEvento={id}`: consulta los sucesos registrados para un evento.
- `POST /api/logs`: centraliza logs estructurados enviados por otros servicios.
- `GET /api/logs?desde={ISO-8601}&hasta={ISO-8601}&servicio={nombre}`: consulta hasta 500 logs del período; `servicio` es opcional.

## Integración pendiente

Las notificaciones se procesan en segundo plano y quedan disponibles dentro del sistema. El envío por correo o SMS requiere integrar un proveedor. Compras debe enviar los sucesos para alimentar los reportes; los demás microservicios deben publicar sus logs para centralizarlos. Si el grupo decide usar RabbitMQ/Kafka, la recepción HTTP de sucesos se puede reemplazar por el broker acordado.

El Gateway publica las rutas de bandeja. El registro de sucesos y logs se consume dentro de la red de servicios. Los sucesos y logs excluyen contraseñas, tokens y datos de tarjetas.
