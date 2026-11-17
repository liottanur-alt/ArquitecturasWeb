# Prueba manual con Postman

Esta guia prueba el circuito completo de compras contra los servicios reales. Los IDs de evento, sector y asiento se crean en Eventos y SectorAsiento; la coleccion de Compras los toma de variables.

## 1. Preparar el entorno

Abrir cuatro terminales en la raiz del repositorio.

```powershell
# Terminal 1 - MySQL compartido
cd .\msv-compraEntardaSol
docker compose -f ../docker-compose.yml up -d --wait mysql

# Terminal 2 - Eventos
cd .\msv-evento
.\mvnw.cmd spring-boot:run

# Terminal 3 - SectorAsiento
cd .\msv-SectoerAsiento
.\mvnw.cmd spring-boot:run

# Terminal 4 - Compras
cd .\msv-compraEntardaSol
.\mvnw.cmd spring-boot:run
```

Esperar los mensajes `Started ...` de cada aplicacion. Comprobar:

- Eventos: <http://localhost:8005/api/eventos>
- SectorAsiento: <http://localhost:8002/sectores>
- Compras: <http://localhost:8003/openapi.json>

## 2. Crear el evento y el inventario

Para preparar datos, seguir la colección de SectorAsiento o ejecutar Test-System.ps1 desde la raíz según [pruebas de sistema](../../tests/system/README.md). Sus IDs quedan en .local/system-tests/integration-security.json. Para una compra numerada nueva se necesita:

1. Un cliente (`POST /usuarios`).
2. Un lugar y un evento futuro publicado (`POST /api/eventos` + `PATCH /api/eventos/{id}/publicar`).
3. Un sector numerado con al menos un asiento, o un sector CAMPO.
4. Inventario configurado (`PUT /eventos/{id}/inventario`).
5. Sector asociado al evento (`PUT /eventos/{id}/sectores`).

Anotar `idCliente`, `idEvento`, `idSector` y `idAsiento`.

## 3. Importar la coleccion

Importar `TicketYa-Compras.postman_collection.json` en Postman. La coleccion define estas variables:

| Variable | Uso |
| --- | --- |
| `baseUrl` | `http://localhost:8003` |
| `idCliente` | Cliente creado en Usuarios |
| `idEvento` | Evento publicado |
| `idSector` | Sector configurado |
| `idAsiento` | Asiento del sector numerado |
| `claveCompra` | Clave unica de la compra |
| `idCompra` | Se completa con la respuesta |
| `idEntrada` | Se completa con la respuesta |
| `idSolicitud` | Se completa con la respuesta |

Cambiar `claveCompra` en cada ejecucion nueva. Las claves de idempotencia no deben reutilizarse con otro contenido.

## 4. Secuencia de requests

1. **Crear compra** `POST /compras`. Espera `201` y `estadoCompra = PENDIENTE`. La coleccion guarda `idCompra`.
2. **Consultar compra** `GET /compras/{idCompra}`. Espera `200`.
3. **Repetir compra** con la misma clave. Espera el mismo `id` y no crea otra compra.
4. **Aprobar pago** `PUT /compras/{idCompra}/pago?aprobado=true`. Espera `estadoCompra = APROBADA` y `referenciaPago` con prefijo `SIM-`.
5. **Listar entradas de la compra** `GET /entradas/compra/{idCompra}`. Espera una entrada por asiento. La coleccion guarda `idEntrada` y `qr`.
6. **Consultar entrada por QR** `GET /entradas/qr/{qr}`. Espera `200`.
7. **Marcar uso** `PATCH /entradas/{idEntrada}/uso`. Espera `estadoQr = ESCANEADO`.
8. **Repetir uso**. Espera `409` porque la entrada ya fue utilizada.

## 5. Devolucion

1. **Crear solicitud** `POST /solicitudes` con `idEntrada`, `idCliente` y una descripcion. Espera `201` y `estadoSolicitud = PENDIENTE`. La coleccion guarda `idSolicitud`.
2. **Listar pendientes** `GET /solicitudes/pendientes`. Debe aparecer la solicitud.
3. **Aprobar** `PUT /solicitudes/{idSolicitud}/resolver` con `estadoSolicitud = ACEPTADA`. Espera `estadoReintegro = COMPLETADO` y `referenciaReintegro` con prefijo `SIM-REF-`.
4. **Repetir la aprobacion**. Espera la misma respuesta sin un segundo reintegro.
5. **Consultar entrada**. Debe quedar `DEVOLUCION_ACEPTADA`.

Si faltan menos de 72 horas para el evento, el paso 1 devuelve `409`. Para probar el camino feliz, crear el evento con al menos siete dias de anticipacion.

Para probar el rechazo, crear otra compra y otra solicitud, y resolver con `estadoSolicitud = RECHAZADA`. La entrada debe quedar `DEVOLUCION_RECHAZADA`.

## 6. Cancelacion de evento

1. Crear y pagar una compra nueva.
2. Ejecutar **Cancelar evento** `POST /compras/eventos/{idEvento}/cancelacion`.
3. Esperar `comprasCanceladas >= 1`, `entradasReintegradas >= 1` e `inventarioCancelado = true`.
4. Consultar la compra. Debe quedar `CANCELADA` con `estadoReintegro = COMPLETADO`.
5. Consultar la entrada. Debe quedar `EVENTO_CANCELADO`.
6. Repetir la cancelacion. Los contadores deben ser cero y no debe cambiar la referencia de reintegro.

Si SectorAsiento responde `404` porque el evento no tenia inventario, `inventarioCancelado` sera `false` y la operacion igual termina bien.

POST /compras/eventos/{idEvento}/cancelacion también cancela el estado del evento en Eventos. No hace falta una segunda llamada al catálogo.

## 7. Casos de error utiles

| Caso | Request | Esperado |
| --- | --- | --- |
| Falta la clave de idempotencia | `POST /compras` sin `claveIdempotencia` | `400` |
| Sin asientos ni cantidad | `POST /compras` solo con datos del evento | `400` |
| Evento inexistente | `POST /compras` con `idEvento` invalido | `404` |
| Reutilizar clave con otro pedido | Misma clave, otro asiento | `409` |
| Aprobar dos veces con distinto resultado | Pagar aprobado y luego rechazado | `409` |
| Entrada de otro cliente | `POST /solicitudes` con otro `idCliente` | `403` |
| Devolucion duplicada | Dos `POST /solicitudes` para la misma entrada | `409` |

## 8. Alternativa por PowerShell

```powershell
$compras = 'http://localhost:8003'
$body = @{
    claveIdempotencia = "compra-$([guid]::NewGuid().ToString('N').Substring(0,8))"
    idCliente = 1
    idEvento = 1
    idSector = 1
    idsAsientos = @(1)
    medioDePago = 'TARJETA'
} | ConvertTo-Json

$compra = Invoke-RestMethod "$compras/compras" -Method Post -ContentType 'application/json' -Body $body
$compra
Invoke-RestMethod "$compras/compras/$($compra.id)/pago?aprobado=true" -Method Put
Invoke-RestMethod "$compras/entradas/compra/$($compra.id)"
```

Ajustar los IDs a los datos reales creados en el paso 2.

## 9. Limpieza

```powershell
cd .\msv-compraEntardaSol
docker compose -f ../docker-compose.yml stop mysql
```

`stop mysql` conserva los datos y detiene el MySQL de los cuatro servicios. El Compose raíz administra también la base compartida; no usar down -v para limpiar únicamente estas pruebas.
