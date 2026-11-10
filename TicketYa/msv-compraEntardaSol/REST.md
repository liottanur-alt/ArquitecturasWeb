# Contrato REST - Compras, entradas y solicitudes

Base local: `http://localhost:8003`. Todas las peticiones y respuestas usan `application/json`.

Formato de error uniforme:

```json
{
  "error": "La compra ya fue procesada con otro resultado",
  "detalle": null,
  "correlationId": "3f2c...",
  "timestamp": "2026-11-10T17:00:00Z"
}
```

## Compras

### POST /compras

Crea una compra y su reserva. Devuelve `201`. Si la `claveIdempotencia` ya existe con el mismo pedido, devuelve la compra original; con otro contenido devuelve `409`.

```json
{
  "claveIdempotencia": "compra-2026-0001",
  "idCliente": 12,
  "idEvento": 40,
  "idSector": 7,
  "idsAsientos": [101, 102],
  "medioDePago": "TARJETA"
}
```

Para un sector sin numerar:

```json
{
  "claveIdempotencia": "compra-campo-0001",
  "idCliente": 12,
  "idEvento": 40,
  "idSector": 8,
  "cantidadEntradas": 3,
  "medioDePago": "EFECTIVO"
}
```

Reglas:

- Se debe informar `idsAsientos` no vacio o `cantidadEntradas` positivo.
- La cantidad maxima es 1000.
- El evento debe estar `PUBLICADO`, no cancelado/agotado/finalizado y con fecha futura.

Respuesta `201`:

```json
{
  "id": 1,
  "idCliente": 12,
  "idEvento": 40,
  "idSector": 7,
  "cantidadEntradas": 2,
  "medioDePago": "TARJETA",
  "descripcion": "Compra pendiente de pago",
  "estadoCompra": "PENDIENTE",
  "claveIdempotencia": "compra-2026-0001",
  "idReserva": 500,
  "precioUnitario": 1500.00,
  "importeTotal": 3000.00,
  "referenciaPago": null,
  "referenciaReintegro": null,
  "estadoReintegro": null,
  "idsAsientos": [101, 102]
}
```

### GET /compras/{id}

Devuelve la compra o `404`.

### GET /compras/cliente/{idCliente}

Lista las compras del cliente, mas recientes primero.

### PUT /compras/{id}/pago?aprobado=true|false

Procesa el pago simulado.

- `true`: confirma la reserva y emite una entrada por lugar. Estado `APROBADA`.
- `false`: libera la reserva. Estado `RECHAZADA`, sin entradas.
- Reserva vencida: estado `EXPIRADA`, `estadoReintegro = COMPLETADO`.
- Repetir el mismo resultado es seguro. Un resultado distinto devuelve `409`.

### POST /compras/eventos/{idEvento}/cancelacion

Cancela las compras del evento y reintegra las entradas vigentes. Tambien cancela el inventario en SectorAsiento.

```json
{
  "idEvento": 40,
  "comprasCanceladas": 2,
  "entradasReintegradas": 3,
  "importeReintegrado": 4500.00,
  "inventarioCancelado": true
}
```

`inventarioCancelado` es `false` si SectorAsiento responde `404` porque el evento no tiene inventario configurado. La operacion es idempotente.

## Entradas

### GET /entradas/{id}

### GET /entradas/qr/{qr}

### GET /entradas/compra/{idCompra}

### GET /entradas/cliente/{idCliente}

Respuesta:

```json
{
  "id": 10,
  "qr": "6f1c2a0e-...",
  "estadoQr": "NO_ESCANEADO",
  "idCliente": 12,
  "idEvento": 40,
  "idSector": 7,
  "idAsiento": 101,
  "precio": 1500.00,
  "estadoEntrada": "PAGA"
}
```

### PATCH /entradas/{id}/uso

### PATCH /entradas/qr/{qr}/uso

Marca el QR como `ESCANEADO`. Devuelve `409` si la entrada no esta `PAGA` o si ya fue utilizada.

## Solicitudes de devolucion

### POST /solicitudes

```json
{
  "idEntrada": 10,
  "idCliente": 12,
  "descripcion": "No puedo asistir al evento"
}
```

Devuelve `201`. Reglas:

- La entrada debe pertenecer al cliente (`403` si no).
- La entrada debe estar `PAGA` o `DEVOLUCION_RECHAZADA`.
- No debe existir una solicitud `PENDIENTE` o `ACEPTADA` para la entrada.
- Deben faltar al menos 72 horas para el evento.
- Registrar la solicitud no libera el lugar ni reintegra dinero.

Respuesta:

```json
{
  "id": 3,
  "idEntrada": 10,
  "idCompra": 1,
  "idCliente": 12,
  "idAdministrador": null,
  "fechaSolicitud": "2026-11-10T17:00:00Z",
  "fechaResolucion": null,
  "referenciaReintegro": null,
  "estadoReintegro": null,
  "descripcion": "No puedo asistir al evento",
  "estadoSolicitud": "PENDIENTE"
}
```

### GET /solicitudes/{id}

### GET /solicitudes/entrada/{idEntrada}

### GET /solicitudes/pendientes

### GET /solicitudes/cliente/{idCliente}

### PUT /solicitudes/{id}/resolver

```json
{
  "idAdministrador": 9,
  "estadoSolicitud": "ACEPTADA"
}
```

- `ACEPTADA`: libera el asiento en SectorAsiento con la fecha original de la solicitud, marca la entrada `DEVOLUCION_ACEPTADA` y ejecuta el reintegro simulado (`estadoReintegro = COMPLETADO`).
- `RECHAZADA`: marca la entrada `DEVOLUCION_RECHAZADA`.
- Repetir el mismo estado es idempotente; otro estado devuelve `409`.
- Estados permitidos: `ACEPTADA` y `RECHAZADA`.

## Codigos de estado

| Codigo | Caso |
| --- | --- |
| 200 | Consulta o transicion correcta |
| 201 | Compra o solicitud creada |
| 400 | Validacion o pedido incompleto |
| 403 | Entrada de otro cliente |
| 404 | Recurso inexistente |
| 409 | Conflicto de negocio o idempotencia |
| 502 | Error devuelto por un servicio requerido |
| 503 | Servicio requerido no disponible |

## Contratos consumidos

| Servicio | Metodo y ruta | Uso |
| --- | --- | --- |
| Eventos | `GET /api/eventos/{id}` | Validar publicacion, estado y fecha |
| SectorAsiento | `POST /reservas` | Crear reserva temporal |
| SectorAsiento | `POST /reservas/{id}/confirmacion` | Confirmar venta |
| SectorAsiento | `POST /reservas/{id}/liberacion` | Liberar por rechazo o cancelacion |
| SectorAsiento | `POST /reservas/{id}/devoluciones` | Liberar por devolucion aprobada |
| SectorAsiento | `POST /eventos/{id}/inventario/cancelacion` | Cerrar inventario del evento |
