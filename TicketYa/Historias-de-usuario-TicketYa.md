# TicketYa | Historias de usuario

> **Alcance** · Entrega 1: 5 funcionalidades y 14 historias. Entrega 2: 2 funcionalidades y 5 historias.

## Entrega 1 · Core del sistema

### Índice de funcionalidades

- [F01 · Catálogo de eventos y lugares](#f01)
- [F02 · Publicación y gestión de eventos](#f02)
- [F03 · Búsqueda de eventos](#f03)
- [F04 · Reserva, pago y emisión de entradas](#f04)
- [F05 · Devoluciones, reintegros y cancelación](#f05)

---

<a id="f01"></a>

### F01 · Catálogo de eventos y lugares


#### HU-01 · Registrar lugares y sectores

> Como administrador, quiero registrar lugares y sus sectores para que los organizadores puedan configurar eventos.

**Criterios de aceptación**

- Dado un lugar válido, cuando lo registro, entonces queda identificado de forma única y se guardan su nombre, dirección y coordenadas.
- Dado un sector numerado, cuando lo configuro, entonces puedo identificar cada asiento por fila y número, sin duplicados dentro del sector.
- Dado un sector sin numerar, cuando lo configuro, entonces queda registrada su capacidad máxima positiva.

#### HU-02 · Crear un evento en borrador

> Como organizador, quiero crear un evento en borrador para preparar sus datos antes de ofrecer entradas.

**Criterios de aceptación**

- Cuando registro nombre, descripción, tipo, fecha y hora, lugar y participantes, el evento recibe un ID, queda asociado a mi usuario y se guarda en estado borrador.
- Puedo asociar los participantes correspondientes al evento, como artistas, equipos o disertantes.
- Puedo consultar y editar mis borradores. Los clientes solo encuentran en la búsqueda pública eventos que hayan sido publicados y estén habilitados para aparecer.

#### HU-03 · Configurar sectores y precios del evento

> Como organizador, quiero seleccionar los sectores habilitados y fijar sus precios para definir la oferta del evento.

**Criterios de aceptación**

- Puedo asignar un precio a cada sector habilitado del lugar. Dos eventos en el mismo lugar pueden tener precios distintos para el mismo sector.
- Si intento agregar un sector de otro lugar o repetir un sector dentro del evento, el sistema rechaza el cambio.
- Si configuro un cupo, este respeta la capacidad del sector y, cuando es numerado, la disponibilidad de asientos configurados.
<a id="f02"></a>

### F02 · Publicación y gestión de eventos


#### HU-04 · Publicar un evento

> Como organizador, quiero publicar un evento para que los clientes puedan encontrarlo y comprar entradas.

**Criterios de aceptación**

- Dado un borrador propio con datos completos y al menos un sector habilitado con precio y capacidad, cuando lo publico, entonces pasa a estado publicado y aparece en la búsqueda.
- Si falta algún dato requerido o la configuración de sectores es inválida, el evento sigue en borrador y el sistema indica qué falta corregir.
- Si otro organizador intenta publicar el evento, el sistema rechaza la operación.

#### HU-05 · Suspender un evento

> Como administrador, quiero suspender un evento para detener nuevas ventas.

**Criterios de aceptación**

- Dado un evento publicado, cuando lo suspendo, entonces el sistema registra la suspensión y deja de aceptar reservas nuevas.
- Las entradas ya emitidas conservan su estado y la suspensión por sí sola no inicia reintegros.

#### HU-06 · Reflejar el agotamiento y la finalización

> Como cliente, quiero conocer el estado del evento para saber si todavía puedo comprar.

**Criterios de aceptación**

- Cuando todos los lugares habilitados están vendidos, el evento figura como agotado y el sistema rechaza nuevas reservas.
- Si una devolución libera capacidad de un evento agotado y la venta sigue habilitada, el evento vuelve a ofrecer esa disponibilidad.
- Un evento finalizado figura como tal y el sistema rechaza nuevas reservas.
<a id="f03"></a>

### F03 · Búsqueda de eventos


#### HU-07 · Buscar, filtrar y ordenar eventos

> Como cliente, quiero buscar eventos por distintos criterios para encontrar opciones que me interesen.

**Criterios de aceptación**

- Puedo filtrar por tipo, organizador, participante, ciudad y rango de fechas, individualmente o combinando criterios.
- Cuando combino filtros, cada resultado cumple todos los filtros aplicados.
- Puedo ordenar los resultados por fecha, precio o relevancia. Para ordenar por precio, se toma el menor precio entre los sectores habilitados de cada evento.
- Si no hay coincidencias, la búsqueda devuelve una lista vacía. Los borradores quedan fuera de los resultados públicos.
<a id="f04"></a>

### F04 · Reserva, pago y emisión de entradas


#### HU-08 · Reservar lugares temporalmente

> Como cliente, quiero reservar los lugares elegidos mientras pago para conservarlos durante el proceso de compra.

**Criterios de aceptación**

- En un sector numerado puedo elegir asientos concretos. En un sector sin numerar puedo indicar cuántas entradas quiero.
- Si hay disponibilidad y la cantidad solicitada respeta el límite por cliente y evento, el sistema crea la reserva e informa cuándo vence.
- Mientras la reserva está vigente, sus lugares quedan fuera de la disponibilidad para otros clientes.
- Si falta disponibilidad para completar la selección o se supera el límite, la operación se rechaza sin retener lugares. Para el límite se cuentan las reservas vigentes y las entradas compradas que siguen activas.

#### HU-09 · Pagar y recibir las entradas

> Como cliente, quiero pagar una reserva para obtener mis entradas con QR.

**Criterios de aceptación**

- Dada una reserva vigente, si el pago se aprueba y la reserva puede confirmarse, el sistema registra la compra y emite una entrada por cada lugar reservado, con un QR único por entrada.
- El detalle registra cliente, evento, sector, cantidad, importe y medio de pago. Para entradas numeradas, también identifica el asiento.
- Cada entrada queda vinculada a la compra y conserva el precio pagado.

#### HU-10 · Liberar lugares por rechazo o vencimiento

> Como cliente, quiero que una reserva fallida se cierre correctamente para conocer el resultado y permitir que esos lugares vuelvan a ofrecerse.

**Criterios de aceptación**

- Si el pago se rechaza, el sistema libera los lugares, informa el rechazo y no emite entradas.
- Si vence la reserva antes de confirmarse, el sistema libera los lugares y rechaza la confirmación con esa reserva.
- Si llega una aprobación después de que la reserva venció y sus lugares se liberaron, el sistema no emite entradas con esa reserva y gestiona la reversión del cobro.

#### HU-11 · Evitar la doble venta y los duplicados por reintento

> Como cliente, quiero que el sistema confirme cada lugar una sola vez para confiar en que mi entrada es válida.

**Criterios de aceptación**

- Dado un asiento disponible, si varios clientes intentan comprarlo simultáneamente, como máximo una compra termina confirmada para ese asiento.
- Dado que queda un solo lugar en un sector sin numerar, si varios clientes intentan comprarlo al mismo tiempo, como máximo una compra termina confirmada y el cupo no se supera.
- Si el cliente reintenta la misma operación de pago por un corte de conexión, el sistema reconoce el intento original y no genera otro cobro ni otro conjunto de entradas.
- Los mismos resultados se mantienen si las solicitudes concurrentes llegan a distintas instancias del servicio.
<a id="f05"></a>

### F05 · Devoluciones, reintegros y cancelación


#### HU-12 · Solicitar la devolución de una entrada

> Como cliente, quiero solicitar la devolución de una entrada propia dentro del plazo permitido.

**Criterios de aceptación**

- Si faltan al menos 72 horas para el evento y la entrada es elegible, el sistema registra la solicitud con fecha y estado pendiente.
- Si faltan menos de 72 horas, la entrada pertenece a otro cliente o ya tiene una devolución pendiente o aprobada, el sistema rechaza la nueva solicitud.
- Registrar una solicitud no libera el lugar ni inicia un reintegro.

#### HU-13 · Resolver una solicitud de devolución

> Como administrador, quiero aprobar o rechazar una solicitud para resolver la devolución del cliente.

**Criterios de aceptación**

- Cuando apruebo una solicitud pendiente, la entrada se marca como devuelta, el asiento o cupo se libera y se solicita el reintegro a la pasarela contra el pago original.
- Cuando rechazo la solicitud, se registra la decisión y la entrada conserva su validez.
- Si se repite la aprobación de la misma solicitud, el sistema no vuelve a liberar el lugar ni solicita un segundo reintegro.
- Si la pasarela aún no confirmó el reintegro, el sistema lo mantiene pendiente. El reintegro figura como completado cuando la pasarela lo confirma.

#### HU-14 · Cancelar un evento y reintegrar las entradas vendidas

> Como administrador, quiero cancelar un evento para cerrar la venta y devolver el dinero a los compradores.

**Criterios de aceptación**

- Cuando cancelo el evento, pasa a estado cancelado y el sistema bloquea nuevas reservas y confirmaciones de compra.
- El sistema identifica las entradas vendidas y solicita los reintegros correspondientes, incluso si los compradores no iniciaron una devolución.
- Si una entrada ya tiene un reintegro iniciado o completado, la cancelación no genera un segundo reintegro por esa entrada.
- El sistema identifica y notifica a los compradores afectados por la cancelación. Este aviso forma parte del flujo del punto 7. La cobertura general de notificaciones se completa en la Entrega 2.

## Entrega 2 · Sistema completo

### Índice de funcionalidades

- [F06 · Fila virtual](#f06)
- [F07 · Caché del catálogo](#f07)

---

<a id="f06"></a>

### F06 · Fila virtual

#### HU-15 · Ingresar a la fila de un evento de alta demanda

> Como cliente, quiero ingresar a una fila virtual cuando comienza la venta de un evento de alta demanda para esperar mi turno de compra.

**Criterios de aceptación**

- Cuando la venta está abierta y el evento está marcado como de alta demanda, el sistema me agrega a la fila y me informa que quedé registrado.
- Si repito la solicitud para el mismo evento, el sistema conserva mi lugar y no crea otra posición para mí.
- Los eventos que no están marcados como de alta demanda siguen permitiendo el acceso al flujo habitual de compra, sin pasar por la fila.

#### HU-16 · Consultar la posición y el estado en la fila

> Como cliente, quiero consultar mi posición y el estado de mi turno para saber cuánto falta para poder comprar.

**Criterios de aceptación**

- El sistema informa si sigo esperando, si ya tengo permiso para comprar o si mi turno venció.
- Mientras espero, mi posición se actualiza cuando otros clientes avanzan, abandonan la fila o vencen.
- Solo puedo consultar la posición y el estado asociados a mi identidad y al evento correspondiente.

#### HU-17 · Comprar por turnos y limitar el acceso simultáneo

> Como cliente, quiero recibir un turno temporal cuando me corresponda para poder iniciar la compra sin que ingresen más compradores simultáneos que los permitidos.

**Criterios de aceptación**

- El sistema habilita clientes en orden de llegada y no supera el máximo de compradores simultáneos configurado para el evento.
- El permiso de compra queda asociado al cliente y al evento, y deja de ser válido al vencer o al completar el turno.
- Una solicitud de compra con un permiso vencido, utilizado por otro cliente o emitido para otro evento se rechaza.
- Si una operación se reintenta con la misma clave de idempotencia, el sistema reconoce el intento original y no crea una compra o reserva duplicada.
- El orden y el límite de compradores se mantienen aunque las solicitudes sean atendidas por distintas instancias del sistema.

<a id="f07"></a>

### F07 · Caché del catálogo

#### HU-18 · Consultar eventos y búsquedas con caché

> Como cliente, quiero consultar rápidamente eventos, detalles y resultados de búsqueda para encontrar opciones disponibles.

**Criterios de aceptación**

- El sistema puede cachear consultas públicas de listados, detalles y búsquedas de eventos publicados.
- Las búsquedas con filtros, orden o paginación diferentes no comparten una respuesta incorrecta: esos parámetros se consideran al identificar cada resultado cacheado.
- El tiempo de vida (TTL) de cada tipo de consulta se puede configurar y, al vencer, el sistema obtiene datos actualizados.
- La disponibilidad de asientos o sectores, las reservas, las posiciones de fila y los turnos de compra no se sirven desde esta caché.

#### HU-19 · Actualizar la caché cuando cambian los eventos

> Como cliente, quiero que el catálogo y las búsquedas reflejen los cambios de los eventos para no recibir información desactualizada.

**Criterios de aceptación**

- Cuando se crea, edita, publica, suspende, cancela o elimina un evento, el sistema invalida o actualiza las respuestas cacheadas que puedan incluirlo.
- Cuando cambia un precio o un estado de venta que se muestra en una consulta pública, el resultado cacheado afectado deja de servirse como vigente.
- Si una invalidación no ocurre, el TTL limita cuánto tiempo puede conservarse la respuesta anterior.
