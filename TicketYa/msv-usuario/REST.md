# API de usuarios

Las rutas son relativas a la URL del microservicio. Para levantar MongoDB y ejecutar
la aplicacion, seguir las instrucciones de [README.md](README.md).

## Usuarios

| Metodo | Ruta | Resultado |
| --- | --- | --- |
| GET | /usuarios | Lista de usuarios |
| GET | /usuarios/{id} | Usuario por ID |
| POST | /usuarios | Crea un usuario (201 y encabezado Location) |
| PUT | /usuarios/{id} | Actualiza los datos del perfil; conserva preferencias y favoritos |
| DELETE | /usuarios/{id} | Elimina usuario y sus favoritos (204) |

Body para POST y PUT (el rol debe existir):

```json
{
  "nombre": "Ana Perez",
  "email": "ana@example.com",
  "dni": "12345678",
  "ciudad": "Tandil",
  "idRol": 1
}
```

La respuesta incluye esos campos y el `id` del usuario. Email y DNI son unicos.

## Preferencias

`PUT /usuarios/{id}/preferencias` reemplaza los tipos de evento preferidos y la ciudad de preferencia. Devuelve 200 con el usuario actualizado. Reutiliza el repositorio de usuarios y persiste estos campos dentro de su documento MongoDB.

```json
{
  "tiposEventoPreferidos": ["RECITAL", "TEATRO", "RECITAL"],
  "ciudadPreferida": "Mar del Plata"
}
```

- Tipos permitidos: `RECITAL`, `TEATRO`, `DEPORTE`, `CONFERENCIA`, `FESTIVAL`, `OTRO`, iguales al contrato actual de Eventos. Se mantienen como enum local para no acoplar las entidades de ambos servicios.
- Los tipos repetidos se guardan una sola vez; el orden no forma parte del contrato.
- La ciudad se recorta y admite hasta 100 caracteres; puede ser distinta de `ciudad` del perfil.
- Para vaciar: `{"tiposEventoPreferidos":[],"ciudadPreferida":""}`. Los campos omitidos o null tambien equivalen a vacio; `{}` limpia ambas preferencias. Es un reemplazo completo, no un PATCH.
- No cambia nombre, email, DNI, ciudad del perfil, rol ni artistas favoritos. `@Version` evita sobrescribir cambios concurrentes (409).
- Usuario inexistente: 404. Body ausente/null, JSON invalido, tipo desconocido/numerico/nulo en la lista, ciudad que no sea texto o longitud excesiva: 400.
- `GET /usuarios`, `GET /usuarios/{id}` y las respuestas de alta/actualizacion incluyen `tiposEventoPreferidos` y `ciudadPreferida`. Los usuarios anteriores se leen con preferencias vacias sin migracion destructiva.

Las preferencias quedan persistidas despues de reiniciar Usuarios. Este endpoint guarda gustos; no genera sugerencias de eventos todavia.

### Consultar preferencias (HU2)

`GET /usuarios/{id}/preferencias` devuelve solo los datos guardados en MongoDB:

```json
{
  "tiposEventoPreferidos": ["RECITAL", "TEATRO"],
  "ciudadPreferida": "Mar del Plata",
  "artistasFavoritos": [15, 28]
}
```

Sin preferencias: `{"tiposEventoPreferidos":[],"ciudadPreferida":null,"artistasFavoritos":[]}`. Los artistas se devuelven como IDs locales: este endpoint no consulta Eventos y funciona aunque este caido. Si solo hay artistas favoritos, esos IDs se devuelven aunque tipos y ciudad esten vacios.

Devuelve 200 si existe el usuario, o 404 si no existe. Es de solo lectura: no guarda, normaliza documentos ni incrementa su version. Tambien admite documentos antiguos sin los campos nuevos, devolviendo valores vacios en la respuesta. El orden de las listas no forma parte del contrato.

La ciudad sin definir es `null` en este DTO especifico; el DTO del perfil usado por `GET /usuarios/{id}` conserva la representacion `""` para ese caso.

## Roles

| Metodo | Ruta | Resultado |
| --- | --- | --- |
| GET | /roles | Lista de roles |
| GET | /roles/{id} | Rol por ID |
| POST | /roles | Crea un rol (201 y encabezado Location) |
| PUT | /roles/{id} | Actualiza el nombre del rol |
| DELETE | /roles/{id} | Elimina un rol sin usuarios asociados (204) |

Body para POST y PUT:

```json
{"name": "CLIENTE"}
```

`name` es un enum y solo admite `CLIENTE`, `ORGANIZADOR`, `ADMINISTRADOR`
y `PERSONAL_ACCESO`, en mayusculas. Otros valores o un tipo ausente devuelven 400.
En la base se guarda el nombre del enum, no su posicion numerica.

## Artistas favoritos

| Metodo | Ruta | Resultado |
| --- | --- | --- |
| GET | /usuarios/{id}/artistas-favoritos | Lista de artistas con ID, nombre y genero |
| PUT | /usuarios/{id}/artistas-favoritos/{idArtista} | Agrega un favorito (204, sin body) |
| DELETE | /usuarios/{id}/artistas-favoritos/{idArtista} | Quita un favorito (204) |

El ID del artista debe ser positivo. Al guardar, este servicio persiste solo la
referencia externa. Al listar consulta `GET /api/artistas/{id}` en el servicio
de eventos, configurado mediante `MSV_EVENTO_URL` (por defecto localhost:8005).

Ejemplo de respuesta del listado:

```json
[{"idArtista":123,"nombre":"Soda Stereo","genero":"Rock"}]
```

Sin favoritos devuelve `[]` y no consulta Eventos. Si Eventos no responde devuelve
503; si un artista ya no existe o devuelve datos incompletos devuelve 502.
Los favoritos guardados no se eliminan ante fallos del servicio externo.

### HU3: garantias de favoritos

- `PUT /usuarios/{id}/artistas-favoritos/{idArtista}`: 204, tambien si el ID ya estaba registrado. Agrega mediante `$addToSet`.
- `DELETE /usuarios/{id}/artistas-favoritos/{idArtista}`: 204, tambien si el favorito no estaba registrado. Quita mediante `$pull`.
- Ambas operaciones aceptan solo IDs de artistas positivos (400 si no lo son) y requieren un usuario existente (404). No consultan ni validan la existencia del artista en Eventos: guardan una referencia externa.
- Solo modifican el array de favoritos y la version de concurrencia. Conservan perfil, rol, tipos preferidos y ciudad de preferencia.
- `GET /usuarios/{id}/artistas-favoritos` consulta nombre y genero actuales mediante Feign. Ante una caida de Eventos devuelve 503; si el artista fue eliminado, 502. No borra favoritos ni otras preferencias.
- `GET /usuarios/{id}/preferencias` permite seguir viendo los IDs locales aunque Eventos este caido.

Las pruebas HTTP de integracion comparan el documento MongoDB para detectar cambios no deseados y verifican respuestas para altas/bajas repetidas, IDs cero/negativos, usuario inexistente, detalles del catalogo y fallos/recuperacion.

## Recomendaciones (HU4)

`GET /usuarios/{id}/recomendaciones/por-preferencias` consulta las preferencias locales y obtiene candidatos con `POST /interno/eventos/recomendaciones/candidatos` mediante Feign. No modifica al usuario ni guarda recomendaciones.

### Puntuacion

| Coincidencia | Puntos | Motivo |
| --- | --- | --- |
| Participa al menos un artista favorito | 5 | Participa un artista favorito |
| Tipo incluido entre los preferidos | 3 | Coincide con un tipo de evento preferido |
| Ciudad del lugar igual a la ciudad de preferencia | 2 | Se realiza en tu ciudad de preferencia |

Cada criterio suma una sola vez, aunque se repita un artista o participen varios favoritos. Maximo 10 puntos. La ciudad se compara ignorando mayusculas, tildes y espacios sobrantes/internos repetidos. No se usa la ciudad del perfil como alternativa.

### Filtros y orden

- Fecha estrictamente futura respecto de `America/Argentina/Buenos_Aires`, la misma zona que usa Eventos para sus fechas locales sin offset.
- Estado de publicacion `PUBLICADO` y estado `EN_CURSO`. Se excluyen borradores, suspendidos, cancelados, finalizados, agotados y estados desconocidos.
- Disponibilidad para venta se toma del estado informado por Eventos; no reserva cupos ni garantiza disponibilidad al momento de comprar.
- Sin coincidencias (0 puntos), el evento no se incluye. Sin preferencias o sin coincidencias se devuelve `[]`.
- Orden: puntuacion descendente, fecha mas cercana primero y, si ambas coinciden, ID ascendente.
- Un resultado por `idEvento`. Si el catalogo repite un ID, se conserva el candidato mejor ordenado; los empates entre copias del mismo ID se resuelven por nombre, tipo, ciudad y motivos para evitar depender del orden de llegada.

Ejemplo de respuesta:

```json
[
  {
    "idEvento": 7,
    "nombre": "Recital",
    "tipo": "RECITAL",
    "fechaHora": "2030-01-02T20:00:00",
    "ciudad": "Rosario",
    "puntuacion": 10,
    "motivos": [
      "Participa un artista favorito",
      "Coincide con un tipo de evento preferido",
      "Se realiza en tu ciudad de preferencia"
    ]
  }
]
```

200 devuelve la lista ordenada. Usuario inexistente: 404 sin consultar Eventos. Si no se puede consultar Eventos, o el catalogo devuelve una respuesta nula/invalida: 503; no se disfraza la indisponibilidad como una lista vacia. Desde HU10, sin preferencias se devuelve [] sin consultar Eventos. Las entradas incompletas que impiden identificar el evento o comprobar su fecha/estado se descartan.

## Compras en comun (HU5)

`GET /usuarios/{id}/recomendaciones/por-compras` obtiene recomendaciones colaborativas. Reutiliza el reloj y los filtros de venta de HU4, pero prioriza compradores distintos en lugar de sumar preferencias.

### Historial y vecinos

Compras ejecuta una sola consulta SQL agregada sobre su propia base:

1. Construye relaciones distintas cliente-evento para compras `APROBADA` con al menos una entrada `PAGA`, `SOLICITUD_DEVOLUCION` o `DEVOLUCION_RECHAZADA`.
2. Excluye compras pendientes, rechazadas, expiradas y canceladas; tampoco cuentan entradas `DEVOLUCION_ACEPTADA` o `EVENTO_CANCELADO`. Una solicitud pendiente aun no es un reintegro.
3. Busca otros clientes con al menos un evento en comun con el historial valido del cliente consultado.
4. Cuenta clientes distintos por cada otro evento comprado por esos vecinos. Varias entradas, varias compras o varios eventos compartidos no multiplican a un mismo cliente.
5. Excluye los eventos del historial propio. Si todas mis entradas de un evento fueron reintegradas, ya no pertenece a ese historial valido.

Un reintegro parcial no elimina la relacion cliente-evento mientras quede una entrada vigente, incluso si otra entrada de esa compra ya tiene reintegro completado. Un historial pasado puede servir de referencia; solamente los candidatos se filtran por fecha futura.

### Validacion y contrato publico

Usuarios valida los candidatos en el catalogo de Eventos: fecha futura en Argentina, `PUBLICADO` y `EN_CURSO`. Descarta ausentes, agotados, cancelados, suspendidos y finalizados. Ordena por `compradoresEnComun` descendente, fecha ascendente e ID ascendente. Devuelve un resultado por evento.

```json
[
  {
    "idEvento": 300,
    "nombre": "Festival Y",
    "tipo": "FESTIVAL",
    "fechaHora": "2030-02-01T20:00:00",
    "ciudad": "Rosario",
    "compradoresEnComun": 2,
    "motivo": "Clientes que compraron Recital X también compraron Festival Y"
  }
]
```

La referencia X es el menor ID del historial propio compartido con al menos uno de los compradores del candidato; es una referencia representativa. La cantidad cuenta la union de vecinos de todo el historial, no solo los vinculados a X. No identifica a esos clientes. Si su nombre ya no esta en el catalogo publico, el texto usa `el evento #ID`. La respuesta no contiene IDs, nombres, compras ni entradas individuales de otros clientes.

Sin historial o coincidencias devuelve 200 con `[]`, sin necesitar Eventos si Compras no devuelve candidatos. Usuario inexistente: 404 antes de consultar otros servicios. Compras inaccesible, contrato agregado invalido o fallo de Eventos al validar candidatos: 503; la consulta no modifica preferencias ni compras.

Contrato entre servicios: `GET http://compras:8003/interno/recomendaciones/clientes/{idCliente}`. Devuelve solo `idEvento`, `compradoresEnComun` e `idEventoReferencia`. El Gateway no enruta `/interno/...`. El filtrado de compras vive en Compras; Usuarios no accede a su base SQL ni recibe identidades de vecinos.

## Recomendaciones unificadas (HU6)

`GET /usuarios/{id}/recomendaciones?limite=10` combina preferencias y compras en comun. `limite` es opcional (10 por defecto) y admite enteros de 1 a 100; otro valor devuelve 400. Usuario inexistente devuelve 404 antes de consultar las fuentes.

**Cambio de contrato:** este endpoint ahora devuelve un objeto con metadatos, en lugar del array de HU4. La lista original de HU4 sigue disponible en `/usuarios/{id}/recomendaciones/por-preferencias`; `/por-compras` conserva su contrato.

Regla de puntuacion: `puntuacionPreferencias + 2 * min(compradoresEnComun, 5)`. Preferencias aporta hasta 10 puntos (artista 5, tipo 3, ciudad 2), y compras hasta otros 10. El conteo real de compradores se conserva en la respuesta aunque supere cinco. El tope equilibra ambas fuentes. Ordena por puntuacion total descendente, fecha ascendente e ID ascendente; el limite se aplica despues de combinar y ordenar.

Cada evento aparece una sola vez, con la union de motivos y fuentes sin repeticiones. Si ambas fuentes incluyen el mismo evento, se suman sus aportes; los datos descriptivos provienen de preferencias. No se suman conteos duplicados de compradores. Cada fuente conserva sus filtros de disponibilidad de HU4/HU5; se vuelve a comprobar que la fecha siga siendo futura al finalizar las consultas. Desde HU8, el filtro comun de historial valido excluye eventos ya comprados de ambas fuentes antes de ordenar y limitar.

```json
{
  "recomendaciones": [
    {
      "idEvento": 300,
      "nombre": "Festival Y",
      "tipo": "FESTIVAL",
      "fechaHora": "2030-02-01T20:00:00",
      "ciudad": "Rosario",
      "puntuacion": 7,
      "puntuacionPreferencias": 3,
      "compradoresEnComun": 2,
      "motivos": [
        "Coincide con un tipo de evento preferido",
        "Clientes que compraron Recital X también compraron Festival Y"
      ],
      "fuentes": ["PREFERENCIAS", "COMPRAS_EN_COMUN"]
    }
  ],
  "parcial": false,
  "fuentesNoDisponibles": [],
  "historialVerificado": true,
  "disponibilidadVerificada": true
}
```

- Ambas fuentes exitosas, historial e inventario verificados: 200, `parcial=false`. Sin coincidencias devuelve `recomendaciones=[]`.
- Una fuente inaccesible: 200 con los resultados disponibles, `parcial=true` y `fuentesNoDisponibles` indicando `PREFERENCIAS` o `COMPRAS_EN_COMUN`.
- Una fuente exitosa sin resultados tambien cuenta como exito: si la otra falla se devuelve 200 parcial con lista vacia, no 503.
- Ambas fuentes inaccesibles: 503. Las dos dependen de Eventos cuando compras aporta candidatos. Sin historial, Compras puede responder vacio sin consultar Eventos.

La consulta no modifica preferencias, favoritos ni compras y no expone identidades de otros clientes. Los consumidores deben leer `recomendaciones` y mostrar el indicador `parcial` cuando corresponda; una respuesta parcial puede cambiar de puntuacion y orden al recuperarse la fuente.

## Datos compartidos por solicitud (HU7/HU10)

`RecomendacionService` centraliza los datos de Eventos y comparte el mismo instante de referencia entre fuentes. Desde HU10 reemplaza la descarga de `GET /api/eventos` por una busqueda filtrada y un lote de IDs faltantes. Reutiliza los datos ya recibidos y no vuelve a pedirlos en el lote; no mantiene cache entre solicitudes. Los endpoints individuales reutilizan la misma coordinacion. Ver HU10 para los contratos y la nueva cantidad de llamadas.

## Exclusion de eventos comprados (HU8)

Usuarios consulta `GET http://compras:8003/interno/recomendaciones/clientes/{idCliente}/historial-valido`. Compras devuelve `{"idsEventos":[100,200]}`: solo IDs distintos y ordenados del cliente consultado, sin datos de otros clientes ni compras individuales. Un cliente sin historial devuelve `{"idsEventos":[]}`; un ID no positivo devuelve 400. El prefijo interno no es enrutado por Gateway.

El historial incluye compras `APROBADA` con alguna entrada `PAGA`, `SOLICITUD_DEVOLUCION` o `DEVOLUCION_RECHAZADA`. Pendientes, rechazadas, canceladas, expiradas, entradas reintegradas y entradas de eventos cancelados quedan fuera. Un reintegro parcial conserva el evento si queda otra entrada vigente, incluso en otra compra. Reintegrar todas sus entradas permite recomendar el evento nuevamente si sigue disponible y coincide con alguna fuente.

`RecomendacionService` aplica el mismo filtro a preferencias y compras, incluyendo los endpoints individuales. En la lista unificada se excluyen los IDs antes de ordenar y limitar; los datos se comparten por solicitud y, desde HU10, se obtienen mediante busqueda y lote de IDs.

El JSON unificado agrega `historialVerificado`:

- `true`: se consulto un historial valido y se aplico la exclusion, aunque ese historial este vacio.
- `false`: Compras no respondio o devolvio un historial invalido. La respuesta lleva `parcial=true`; puede contener eventos ya comprados porque no se pudo verificar su exclusion. Si preferencias funciona, sus resultados siguen disponibles.

`fuentesNoDisponibles` informa fallos en las fuentes de recomendaciones. Puede estar vacio con `parcial=true` si solo fallo la consulta del historial. Si ambas fuentes fallan, se conserva el 503 de HU6.

Los endpoints `/por-preferencias` y `/por-compras` conservan sus arrays y agregan las cabeceras `X-Historial-Verificado` y `X-Respuesta-Parcial`. Si el historial no pudo verificarse pero la fuente funciona, devuelven 200 con cabeceras `false` y `true` respectivamente. Un fallo en la propia fuente conserva su error 503. La consulta no modifica datos y no cachea el historial entre solicitudes.

## Disponibilidad real del inventario (HU9)

Usuarios envia `POST http://sectores:8002/interno/inventario/disponibilidad` con `{"idsEventos":[100,200]}`. Recibe `[{"idEvento":100,"disponible":true},{"idEvento":200,"disponible":false}]`. Es un contrato interno no enrutado por Gateway.

El filtro comun consulta una sola vez el lote de IDs distintos de candidatos futuros que quedan despues de excluir las compras propias. No consulta todo el catalogo ni aplica el limite antes del filtro. Ambas fuentes y sus endpoints individuales excluyen los eventos sin entradas; puntuaciones, motivos y orden de los restantes se conservan. Sin candidatos no se llama a Sectores.

Sectores verifica cupos libres de campo o asientos numerados disponibles, descontando ventas y reservas vigentes. Vence las reservas temporales caducadas mediante la misma logica y bloqueos usados al reservar. Devuelve false si el evento no tiene inventario, no tiene sectores configurados o esta cancelado. Usuarios sigue comprobando publicacion y fecha con los datos compartidos de Eventos; el lote de inventario no consulta Eventos.

El JSON unificado agrega `disponibilidadVerificada`. Es true si el lote completo se verifico, aunque todos esten agotados, o si no quedaron candidatos. Si Sectores falla o el contrato llega incompleto/invalido, conserva candidatos con `disponibilidadVerificada=false` y `parcial=true`. `fuentesNoDisponibles` puede seguir vacio porque las fuentes de recomendaciones funcionaron; este indicador describe el filtro de inventario.

Los endpoints individuales conservan arrays y agregan `X-Disponibilidad-Verificada`. `X-Respuesta-Parcial=true` si no pudo verificarse el historial o la disponibilidad. Los indicadores son independientes: el historial puede estar verificado aunque el inventario no lo este.

La recomendacion no crea reservas ni garantiza cupos futuros o limites personales. La compra vuelve a validar inventario, estado del evento y limite por cliente al reservar. El lote lleva `Cache-Control: no-store` y puede persistir la liberacion de reservas vencidas, como las consultas individuales del inventario.

## Consultas acotadas a los candidatos (HU10)

Usuarios ya no descarga `GET /api/eventos`. El coordinador utiliza dos contratos internos de Eventos:

- `POST /interno/eventos/recomendaciones/candidatos`: tipos preferidos, IDs de artistas favoritos, ciudad preferida y `fechaDesde`. Coincide por artista **o** tipo **o** ciudad; la ciudad conserva la normalizacion de tildes, mayusculas y espacios. Solo devuelve publicados, EN_CURSO y posteriores al instante indicado.
- `POST /interno/eventos/recomendaciones/por-ids`: recibe `{"idsEventos":[7,100]}`. Obtiene los candidatos de Compras y sus eventos de referencia en un lote, incluyendo referencias pasadas publicadas para conservar el texto de los motivos. Los IDs inexistentes o no publicados se omiten.

Ambas respuestas usan el DTO minimo de recomendacion: ID, nombre, tipo, fecha, ciudad, IDs de participantes y estados. El coordinador conserva los datos de la busqueda durante la solicitud y pide por lote solo los IDs faltantes. Como maximo hace una busqueda y un lote; omite consultas si no hay criterios o IDs. No hace una llamada por evento, no pagina ni limita en Eventos antes de calcular los puntos combinados.

Se conservan puntuaciones, motivos, exclusiones por historial/inventario y desempate por fecha/ID. El limite final sigue siendo 10 por defecto, entre 1 y 100, despues de combinar y filtrar todos los candidatos. El JSON publico y las cabeceras de HU8/HU9 no cambian. Si falla la busqueda pero funciona el lote, puede aportar Compras, y viceversa; ambas fuentes fallidas mantienen 503. Sin preferencias y sin candidatos de compras puede responder vacio sin depender de Eventos.

La busqueda por preferencias usa OR; no reutiliza el endpoint publico `/api/eventos/buscar`, cuyos filtros siguen combinandose con AND y paginacion para su uso original. Los nuevos contratos estan fuera de las rutas del Gateway.

## Errores

- 400: campos obligatorios ausentes o invalidos, o JSON incorrecto.
- 404: usuario o rol inexistente.
- 409: email, DNI o nombre de rol duplicado; rol en uso; otra restriccion de integridad.

## Autenticacion diferida

La version activa no requiere token ni contrasena. Crear cuentas con POST /usuarios e idRol obtenido de GET /roles. Las rutas /auth/login y /auth/registro estan retiradas y devuelven 404.

Los roles siguen siendo datos del dominio; en esta etapa no restringen acceso a los endpoints. La autenticacion y autorizacion se incorporaran cuando se restaure y adapte el modulo JWT.

## Verificacion

`mvnw.cmd test` ejecuta pruebas unitarias, persistencia MongoDB y CRUD HTTP sin token. Requiere un servidor MongoDB disponible; usa una base temporal aislada que se elimina al terminar.

## Contrato OpenAPI y colección de pruebas

El contrato OpenAPI 3.0.3 está en [src/main/resources/static/openapi.json](src/main/resources/static/openapi.json) y se sirve directamente en http://localhost:8004/openapi.json cuando Usuarios está iniciado.

Importar [TicketYa-Usuarios.postman_collection.json](TicketYa-Usuarios.postman_collection.json) en Postman. Incluye 74 solicitudes ordenadas con HU1 a HU9, captura de IDs, validacion de respuestas y limpieza de sus usuarios y artistas. El script Test-Artistas-Reales.ps1 ejecuta dos iteraciones; los datos externos son opcionales. Ver README.md para los comandos.
