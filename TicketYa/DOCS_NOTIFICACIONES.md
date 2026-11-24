# Bandeja de notificaciones de TicketYa

El panel vive en `frontend/src/components/NotificationInbox.astro`, montado desde `AccountMenu.astro`. `frontend/src/scripts/notifications.ts` sincroniza la sesión, consulta la bandeja y actualiza la lectura. El drawer nativo permite cerrar con Escape y devuelve el foco a la campana. La campana consulta al abrir, al recuperar visibilidad y al cambiar de sesión. Con la página visible y el drawer cerrado también actualiza el contador una vez por minuto.

## Contrato del Gateway

Todos los endpoints requieren `Authorization: Bearer <JWT>`. El Gateway reenvía el token y Notificaciones vuelve a validar firma, emisor y vigencia. El destinatario se obtiene del `sub` del JWT. La ruta histórica `GET /api/notificaciones/clientes/{idCliente}` devuelve el array y requiere que el ID coincida con el titular.

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | `/api/notificaciones` | `200`. Hasta 100 avisos del más reciente al más antiguo. El contador incluye todos los avisos sin leer del titular. |
| PATCH | `/api/notificaciones/{id}/lectura` | `204`. Marca el aviso como leído. Devuelve `404` si el aviso no está en la bandeja. |
| PATCH | `/api/notificaciones/lectura` | `204`. Marca toda la bandeja del titular. |

```json
{
  "noLeidas": 1,
  "notificaciones": [{
    "id": 14,
    "idCliente": 7,
    "asunto": "Compra confirmada",
    "mensaje": "Tu compra 49 fue confirmada.",
    "estado": "OMITIDA",
    "fechaCreacion": "2026-11-24T12:15:00Z",
    "fechaLectura": null
  }]
}
```

`estado` corresponde al envío de correo. `fechaLectura` representa la lectura en el panel. Las dos operaciones PATCH son idempotentes y no reciben cuerpo. La columna de lectura admite null para registros previos. En Docker Compose, Gateway usa `MSVC_NOTIFICACIONES_URL` y Notificaciones requiere el mismo `JWT_SECRET` que Gateway.

## Publicación de sucesos

Los productores publican internamente en `POST /api/sucesos` después de confirmar su operación. Usá una `claveIdempotencia` estable por operación, `tipo`, `idCliente` tomado del registro de negocio e IDs de evento y compra cuando apliquen. El destinatario de correo es opcional para el panel. El servicio genera el título y detalle; el navegador nunca publica sucesos.

Compras emite `COMPRA_CONFIRMADA`. La resolución administrativa emite `DEVOLUCION_APROBADA` o `DEVOLUCION_RECHAZADA` después del commit. También existen plantillas para eventos cancelados o modificados y artistas favoritos. Las entregas usan la integración posterior al commit existente. Un fallo del servicio de notificaciones se registra y no revierte la compra o el reintegro.

Para sumar avisos de ingreso o recordatorios, agregá su tipo y plantilla en Notificaciones y un productor posterior al registro de acceso o un programador del lado servidor, respectivamente. Para ingreso, usá el titular de la entrada y una clave estable por entrada. Para recordatorios, usá una clave por evento, cliente y ventana. Estos dos productores aún no están configurados.
