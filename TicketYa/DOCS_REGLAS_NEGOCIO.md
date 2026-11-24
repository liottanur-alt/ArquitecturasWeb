# Propiedad y elegibilidad para rechazos y devoluciones

El Gateway y Compras autentican el JWT. El servicio obtiene el ID de usuario del `sub` del token. Los campos históricos `idCliente` e `idAdministrador` del cuerpo no sustituyen esa identidad.

| Operación | Reglas | Respuestas |
| --- | --- | --- |
| `POST /solicitudes` | El usuario autenticado debe ser titular de la entrada y de la compra asociada. La compra debe estar `APROBADA`, la entrada en `PAGA` y el QR en `NO_ESCANEADO`. El evento no puede estar cancelado o finalizado. Se requieren al menos 72 horas de anticipación en hora de Buenos Aires. | `201` solicitud pendiente, `400` estado o plazo inválido, `403` titular distinto, `404` entrada o evento inexistente. |
| `PUT /solicitudes/{id}/resolver` | Solo `ADMINISTRADOR`. Es una operación de revisión sobre una solicitud ya creada por su titular. Compras revalida que la solicitud, la entrada y la compra tengan el mismo cliente y que la compra siga aprobada, la entrada siga en `SOLICITUD_DEVOLUCION` y el QR esté sin usar. | `200` resolución o repetición de la misma resolución, `400` estado inválido o resolución incompatible, `403` rol o titular incompatible, `404` solicitud inexistente. |
| `PUT /compras/{id}/pago?aprobado=false` | Rechazo del **pago simulado** por el dueño de una compra pendiente. El propietario se verifica antes del pago y de cualquier respuesta idempotente. Una compra aprobada se devuelve por la ruta de solicitudes. | `200` rechazo o repetición del mismo resultado, `400` otro estado incompatible, `403` compra ajena, `404` compra inexistente. |

Una entrada devuelta, con devolución rechazada o con solicitud pendiente no admite una nueva solicitud. El bloqueo de la fila de entrada serializa la solicitud y el escaneo. El bloqueo de la solicitud serializa dos resoluciones concurrentes. Los errores de negocio incluyen un mensaje descriptivo. Las rutas protegidas responden `401` sin sesión válida.

La cancelación global con reintegro de evento (`POST /compras/eventos/{id}/cancelacion`) es una operación administrativa. Gateway y Compras requieren el rol `ADMINISTRADOR` antes de ejecutarla.

La lista de entradas del perfil se valida contra el usuario actual. Se ofrece «Solicitar devolución» únicamente para entradas propias pagadas con QR sin escanear. Al abrir el diálogo se vuelve a verificar la entrada y se informa el plazo. Si el servidor rechaza el envío, el diálogo muestra su mensaje y la página actualiza las entradas y solicitudes.
