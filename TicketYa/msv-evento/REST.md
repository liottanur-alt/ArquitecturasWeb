# API REST de Eventos

Base actual: http://localhost:8005. Sin autenticación JWT. Todas las rutas del catálogo comienzan con `/api`. El Gateway actual todavía no las enruta.

Contrato importable: [src/main/resources/static/openapi.json](src/main/resources/static/openapi.json), servido en http://localhost:8005/openapi.json. Incluye parámetros, esquemas, ejemplos de requests/responses y códigos de error.

## Lugares

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | /api/lugares | Lista completa, 200 |
| POST | /api/lugares | Crea, 201 |
| GET | /api/lugares/{id} | Consulta, 200 o 404 |
| PUT | /api/lugares/{id} | Reemplaza campos, 200 o 404 |
| DELETE | /api/lugares/{id} | Elimina, 204 o 404; restricciones referenciales pueden producir 500 |

Body de creación/actualización:

```json
{"nombre":"Teatro de prueba","direccion":"Calle 123","latitud":-37.3288,"longitud":-59.1367,"ciudad":"Tandil","tipo":"TEATRO"}
```

Tipos: ESTADIO, TEATRO, SALA y OTRO. El response contiene los mismos campos y un id generado.

## Artistas

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | /api/artistas | Lista completa, 200 |
| POST | /api/artistas | Crea, 201 |
| GET | /api/artistas/{id} | Consulta, 200 o 404 |
| PUT | /api/artistas/{id} | Reemplaza campos, 200 o 404 |
| DELETE | /api/artistas/{id} | Elimina, 204 o 404; restricciones referenciales pueden producir 500 |

Body:

```json
{"nombre":"Banda de prueba","genero":"Rock"}
```

Response: id, nombre y genero. El campo de identificación se llama id, no idArtista.

## Eventos

| Método | Ruta | Resultado |
| --- | --- | --- |
| GET | /api/eventos | Lista completa, incluidos borradores, 200 |
| POST | /api/eventos | Crea EN_CURSO/BORRADOR, 201 |
| GET | /api/eventos/{id} | Consulta, 200 o 404 |
| PUT | /api/eventos/{id} | Reemplaza datos editables, conserva estados, 200 o 404 |
| DELETE | /api/eventos/{id} | Elimina, 204 o 404 |
| PATCH | /api/eventos/{id}/publicar | BORRADOR a PUBLICADO, 200; conflicto 409 |
| PATCH | /api/eventos/{id}/suspender | PUBLICADO a SUSPENDIDO, 200; conflicto 409 |
| PATCH | /api/eventos/{id}/cancelar | Estado CANCELADO y publicación SUSPENDIDO, 200; conflicto 409 |
| GET | /api/eventos/buscar | Array de eventos publicados filtrados, 200 |

Ejemplo de creación/actualización (IDs ilustrativos; usar los obtenidos al crear registros):

```json
{
  "nombre":"Recital de prueba",
  "descripcion":"Evento de demostración",
  "tipo":"RECITAL",
  "idOrganizador":900001,
  "participantesIds":[17],
  "fechaHora":"2030-12-20T21:00:00",
  "idLugar":23,
  "demanda":false
}
```

Tipos: RECITAL, TEATRO, DEPORTE, CONFERENCIA, FESTIVAL y OTRO. El lugar y los artistas deben existir en la base de Eventos. idOrganizador es una referencia externa que no se valida contra Usuarios. fechaHora es una fecha y hora local sin Z ni offset; actualmente no se valida que sea futura.

Aunque el DTO acepta estado y estadoPublicacion, el servicio ignora esos campos al crear/actualizar. Al crear fija EN_CURSO/BORRADOR. Para publicar, suspender o cancelar se usan los PATCH sin body. No existe una transición para republicar SUSPENDIDO: publicar exige BORRADOR. Cancelar exige que el estado no sea CANCELADO ni FINALIZADO; repetir cancelación devuelve 409.

Ejemplo abreviado de respuesta:

```json
{
  "id":42,
  "nombre":"Recital de prueba",
  "descripcion":"Evento de demostración",
  "tipo":"RECITAL",
  "idOrganizador":900001,
  "participantes":[{"id":17,"nombre":"Banda de prueba","genero":"Rock"}],
  "fechaHora":"2030-12-20T21:00:00",
  "lugar":{"id":23,"nombre":"Teatro de prueba","direccion":"Calle 123","latitud":-37.3288,"longitud":-59.1367,"ciudad":"Tandil","tipo":"TEATRO"},
  "estado":"EN_CURSO",
  "estadoPublicacion":"BORRADOR",
  "demanda":false
}
```

No hay encabezado Location en las respuestas 201 actuales. Las consultas individuales de eventos no exigen que el evento esté publicado.

## Búsquedas

Filtros opcionales combinables por AND: tipo, idOrganizador, idParticipante, ciudad, fechaDesde y fechaHasta. Ciudad compara sin distinguir mayúsculas. Solo se incluyen registros con estadoPublicacion PUBLICADO.

Paginación: pagina comienza en 0 (por defecto 0), tamanio por defecto 10. La respuesta es un array, sin total de elementos ni metadatos de Page. Valores negativos de pagina o tamanio no positivo no tienen un error HTTP de negocio configurado y pueden terminar en 500.

Orden admitido: fechaHora o nombre, con dirección asc/desc. El valor por defecto es fechaHora,asc. Precio y relevancia todavía no están implementados.

```http
GET /api/eventos/buscar?tipo=RECITAL&ciudad=Tandil&idParticipante=17&fechaDesde=2030-01-01T00:00:00&fechaHasta=2030-12-31T23:59:59&pagina=0&tamanio=10&ordenar=fechaHora,asc
```

## Errores y límites actuales

- 400: JSON o enums mal formados, tipos de parámetros inválidos, fecha no convertible y ordenamiento inválido.
- 404: artista, lugar o evento no encontrado; referencias inexistentes al guardar eventos.
- 409: publicar/suspender/cancelar desde un estado no admitido.
- 500: casos aún sin manejo de negocio, como omitir idLugar, restricciones al borrar entidades usadas por eventos o valores de paginación inválidos.

Los DTOs/controladores todavía no tienen Bean Validation. No se documenta como garantizado un 400 para nombre vacío, fecha pasada u otros campos obligatorios del enunciado: esa validación sigue pendiente. Los mensajes y campos opcionales del error dependen de la configuración de Spring; status identifica su código HTTP.

Cancelar Eventos solo cambia su base local. No cancela inventario ni pide reintegros/notificaciones automáticamente. La coordinación con SectorAsiento y Compras sigue pendiente. La colección de este módulo no crea reservas.

Colección y datos: [TicketYa-Eventos.postman_collection.json](TicketYa-Eventos.postman_collection.json), [datos-prueba.postman.json](datos-prueba.postman.json). Instrucciones: [docs/PRUEBAS-POSTMAN.md](docs/PRUEBAS-POSTMAN.md).
