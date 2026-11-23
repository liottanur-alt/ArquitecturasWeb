# Integración frontend con TicketYa

Esta guía describe el código disponible en este checkout. Incluye los contratos expuestos por el Gateway y distingue los contratos internos y las funciones incompletas. Se basa en controladores, DTO, servicios, repositorios, dependencias y configuración, no en funcionalidades previstas en historias de usuario.

**Verificación:** revisión estática. No se ejecutaron solicitudes contra el backend durante esta generación. Todos los ejemplos son **ilustrativos**, con datos ficticios, derivados de los contratos; no representan respuestas verificadas ni recursos que necesariamente existan. No se incluyen credenciales ni tokens reales.

## 1. Conexión y convenciones

La URL local predeterminada es `http://localhost:8080`. El puerto del Gateway es `8080` (`api-gateway/src/main/resources/application.yml`). Compose publica `127.0.0.1:8080:8080`, accesible desde el anfitrión. No hay prefijo global ni versionado global.

En todas las tablas, **G** representa la URL configurable del Gateway sin barra final. `G/usuarios` significa, en local, `http://localhost:8080/usuarios`. Las rutas se conservan sin reescritura.

| Prefijos/rutas publicados | Destino local predeterminado | Variable del Gateway |
| --- | --- | --- |
| `/usuarios`, `/usuarios/**`, `/roles`, `/roles/**` | Usuarios: `http://localhost:8004` | `MSVC_USUARIOS_URL` |
| `/api/eventos`, `/api/eventos/**`, `/api/lugares`, `/api/lugares/**`, `/api/artistas`, `/api/artistas/**` | Eventos: `http://localhost:8005` | `MSVC_EVENTOS_URL` |
| `/sectores`, `/sectores/**`, `/asientos`, `/asientos/**`, `/reservas`, `/reservas/**`, `/eventos/*/inventario`, `/eventos/*/inventario/**`, `/eventos/*/sectores`, `/eventos/*/sectores/**` | SectorAsiento: `http://localhost:8002` | `MSVC_SECTORES_URL` |
| `/compras`, `/compras/**`, `/entradas`, `/entradas/**`, `/solicitudes`, `/solicitudes/**` | Compras: `http://localhost:8003` | `MSVC_COMPRAS_URL` |

Compose sustituye los destinos por nombres de servicios de su red. Esos nombres no son URLs para el navegador. Eventos usa `/api/eventos`; disponibilidad usa `/eventos/{idEvento}/sectores`: son rutas distintas. Los patrones `/**` no implementan operaciones: por ejemplo, no existe `GET /compras` ni `GET /reservas`.

### CORS

No se encontró configuración CORS explícita en Gateway ni servicios: sin `@CrossOrigin`, `CorsFilter` ni reglas CORS de Spring MVC/Gateway. No hay orígenes, métodos, credenciales o headers expuestos configurados para documentar como permitidos.

Un frontend en otro puerto/origen puede quedar bloqueado por el navegador. JSON, Authorization, X-Organizador-Id, PUT, PATCH y DELETE pueden requerir preflight. Postman no verifica CORS. Para conectar desde otro origen hace falta configurar CORS en backend o un proxy frontend que presente el API bajo el mismo origen. Ese proxy es una solución a configurar, no una función del backend. No usar `mode: "no-cors"`, que impediría leer la respuesta.

Si se configura CORS, también deberán exponerse los headers que la UI necesite leer: `X-Historial-Verificado`, `X-Disponibilidad-Verificada`, `X-Respuesta-Parcial`, `X-Correlation-Id`, `Retry-After`, `Location`.

### Convenciones aplicables a todos los endpoints

- Headers: `Accept: application/json` recomendado; `Content-Type: application/json` cuando haya cuerpo JSON. Los adicionales se indican por operación. No se requiere token.
- Todos los parámetros `{...}` de ruta son obligatorios. IDs: enteros `Long`; en Sectores/Asientos/Inventario/Reservas los servicios exigen positividad. Otros módulos no aplican esa validación de forma uniforme.
- `—` significa sin cuerpo o sin parámetros adicionales. Los nombres de cuerpos/respuestas remiten a los esquemas de cada sección. Una lista es un array, incluso vacío. Los PUT de datos reemplazan los campos copiados por el servicio, no son PATCH.
- `201` devuelve el objeto creado; `204` no tiene cuerpo. No intentar parsear JSON en un `204`.
- Además de los errores específicos de las tablas, puede haber errores de JSON, conversión de tipos, headers/query faltantes e infraestructura. Su representación depende del módulo (sección 11).
- `fechaHora` es ISO `LocalDateTime` **sin offset**, interpretado por negocio en `America/Argentina/Buenos_Aires`, por ejemplo `2030-12-20T21:00:00`. No agregar `Z` automáticamente.
- `venceEn`, `solicitadaEn`, `fechaSolicitud`, `fechaResolucion`, `timestamp` son instantes ISO con zona, por ejemplo `2030-12-01T15:00:00Z`.
- Importes son números JSON desde `BigDecimal`. No hay campo de moneda implementado. IDs Long muy grandes pueden exceder precisión segura de JavaScript.
- Sólo existe paginación configurable en búsqueda de eventos. No agregar parámetros genéricos de página/orden a otras listas esperando que se apliquen.

## 2. Autenticación y autorización

`POST G/usuarios` crea un perfil, no una cuenta autenticada: no recibe contraseña ni devuelve token. No hay endpoints implementados de login, logout, refresh, recuperación de contraseña o verificación de email. `/auth/login` no está publicado por el Gateway.

No hay validación JWT, firma, claims, expiración, revocación ni renovación. No se puede indicar duración del token o un flujo de refresh existente. Enviar `Authorization: Bearer <token>` es la convención ilustrada en el helper de integración, pero **hoy no autentica ni concede permisos**. No incluir un token ficticio como si fuera emitido por el servidor.

Roles existentes como datos: `CLIENTE`, `ORGANIZADOR`, `ADMINISTRADOR`, `PERSONAL_ACCESO`. **Todos los endpoints de las tablas están disponibles sin rol verificado**, incluyendo pagos, mutación de roles, cancelación, devoluciones y QR. No hay una matriz de permisos aplicada por endpoint.

Comprobaciones puntuales implementadas:

| Operaciones | Comprobación |
| --- | --- |
| PUT evento; PATCH publicar/finalizar | `X-Organizador-Id` obligatorio, debe coincidir con el organizador guardado; si difiere, 403 |
| GET `/api/eventos/mis-eventos` | Header `X-Organizador-Id` obligatorio utilizado como filtro |
| POST `/solicitudes` | `idCliente` del cuerpo debe coincidir con el de la entrada; si difiere, 403 |

Son IDs proporcionados por el cliente, no identidad autenticada. `idAdministrador` positivo no prueba que exista ni tenga ese rol. Ocultar botones en la UI no reemplaza autorización del servidor.

## 3. Usuarios, roles, preferencias y favoritos

Sin headers adicionales ni query. Todas las rutas se agregan a G.

| Método | Ruta | Acción / cuerpo | Éxito | Errores de negocio |
| --- | --- | --- | --- | --- |
| GET | `/usuarios` | Listar perfiles; — | 200 Usuario[] | Infraestructura |
| GET | `/usuarios/{id}` | Obtener; — | 200 Usuario | 404 usuario |
| POST | `/usuarios` | Crear; UsuarioRequest | 201 Usuario, Location `/usuarios/{id}` | 400 datos, 404 rol, 409 duplicado/concurrencia |
| PUT | `/usuarios/{id}` | Reemplazar perfil; UsuarioRequest | 200 Usuario | 400, 404 usuario/rol, 409 |
| DELETE | `/usuarios/{id}` | Borrar perfil y favoritos locales; — | 204 | 404, posible 409 concurrente |
| GET | `/roles` | Listar; — | 200 Rol[] | Infraestructura |
| GET | `/roles/{id}` | Obtener; — | 200 Rol | 404 |
| POST | `/roles` | Crear; RolRequest | 201 Rol, Location `/roles/{id}` | 400 tipo, 409 duplicado |
| PUT | `/roles/{id}` | Reemplazar; RolRequest | 200 Rol | 400, 404, 409 duplicado |
| DELETE | `/roles/{id}` | Borrar; — | 204 | 404, 409 rol con usuarios |
| GET | `/usuarios/{id}/preferencias` | Leer datos locales; — | 200 Preferencias | 404 |
| PUT | `/usuarios/{id}/preferencias` | Reemplazar preferencias; PreferenciasRequest | 200 **Usuario** | 400 tipos/ciudad, 404, 409 concurrencia |
| GET | `/usuarios/{id}/artistas-favoritos` | Resolver artistas en Eventos; — | 200 ArtistaFavorito[] | 404 usuario, 502 artista borrado/incompleto, 503 dependencia |
| PUT | `/usuarios/{id}/artistas-favoritos/{idArtista}` | Agregar ID sin duplicar; — | 204 | 400 artista no positivo, 404 usuario |
| DELETE | `/usuarios/{id}/artistas-favoritos/{idArtista}` | Quitar ID, incluso si no estaba; — | 204 | 400 artista no positivo, 404 usuario |

### Cuerpos y validaciones

`UsuarioRequest`: todos obligatorios. Textos no blancos, se guardan con trim: `nombre` máximo 100, `email` máximo 254 y patrón `[^\s@]+@[^\s@]+\.[^\s@]+`, `dni` máximo 20, `ciudad` máximo 100 (longitudes tras trim); `idRol` positivo y existente. No exige DNI numérico ni convierte email a minúsculas. Email y DNI tienen índices únicos. PUT conserva preferencias/favoritos y reemplaza datos del perfil.

Ejemplo ilustrativo de cuerpo:

```json
{"nombre":"Ana Ejemplo","email":"ana@example.com","dni":"12345678","ciudad":"Tandil","idRol":1}
```

`RolRequest`: `{"name":"CLIENTE"}`. Campo obligatorio, uno de los cuatro roles de sección 2, único. Consultar `/roles`, no asumir IDs fijos.

`PreferenciasRequest`: `tiposEventoPreferidos` array de `RECITAL`, `TEATRO`, `DEPORTE`, `CONFERENCIA`, `FESTIVAL`, `OTRO`, sin elementos nulos; deduplica repetidos. `ciudadPreferida` texto/null, trim, máximo 100. Campo omitido/null vacía su preferencia. Ambos campos se reemplazan; no se deriva ciudad preferida del perfil. Favoritos se modifican aparte.

```json
{"tiposEventoPreferidos":["RECITAL","FESTIVAL"],"ciudadPreferida":"Tandil"}
```

Agregar/quitar favorito no valida existencia del artista en Eventos. Al listar favoritos resuelve todos sus datos: uno eliminado puede hacer fallar toda la consulta. GET preferencias sólo lee IDs locales y no depende de Eventos. Arrays provenientes de sets no garantizan orden.

### Respuestas ilustrativas

Usuario (listas contienen objetos de esta forma):

```json
{"id":7,"nombre":"Ana Ejemplo","email":"ana@example.com","dni":"12345678","ciudad":"Tandil","idRol":1,"tiposEventoPreferidos":["RECITAL"],"ciudadPreferida":"Tandil"}
```

Rol: `{"id":1,"name":"CLIENTE"}`. Preferencias:

```json
{"tiposEventoPreferidos":["RECITAL"],"ciudadPreferida":"Tandil","artistasFavoritos":[3]}
```

Sin ciudad preferida, Preferencias devuelve null y Usuario devuelve `""`. ArtistaFavorito usa `idArtista`, a diferencia del catálogo que usa `id`:

```json
[{"idArtista":3,"nombre":"Artista de ejemplo","genero":"Rock"}]
```

## 4. Recomendaciones

Sin headers de request adicionales.

| Método | Ruta | Query / cuerpo | Éxito | Errores |
| --- | --- | --- | --- | --- |
| GET | `/usuarios/{id}/recomendaciones` | `limite` entero opcional, default 10, entre 1 y 100; — | 200 Recomendaciones | 400 límite, 404 usuario, 503 ambas fuentes fallan |
| GET | `/usuarios/{id}/recomendaciones/por-preferencias` | — | 200 RecomendacionPreferencia[] | 404 usuario, 503 fuente Eventos |
| GET | `/usuarios/{id}/recomendaciones/por-compras` | — | 200 RecomendacionCompra[] | 404 usuario, 503 Compras/Eventos o respuesta remota inválida |

Los endpoints individuales incluyen headers `X-Historial-Verificado`, `X-Disponibilidad-Verificada`, `X-Respuesta-Parcial` (`true`/`false`). La unificada incluye flags en el cuerpo. Sin paginación ni orden configurables en las fuentes individuales.

Se recomiendan eventos futuros PUBLICADO/EN_CURSO con datos válidos. Preferencias suma una vez por criterio: artista favorito +5, tipo +3, ciudad +2. Ciudad se normaliza sin acentos, sin distinguir mayúsculas y normalizando espacios. Orden: puntuación descendente, fecha ascendente, ID ascendente. Compras en común cuenta compradores distintos, sin exponer identidades, ordena cantidad descendente, fecha e ID ascendentes.

Unificada deduplica por evento y suma preferencias + `2 * min(compradoresEnComun, 5)`, ordena puntuación/fecha/ID y aplica límite al final. Excluye historial válido e inventario sin cupo cuando logra verificarlos. Historial válido: compra APROBADA con entrada PAGA, SOLICITUD_DEVOLUCION o DEVOLUCION_RECHAZADA.

Si una fuente falla puede dar 200 parcial; si ambas fallan, 503. Si falla verificación de historial o inventario, puede devolver candidatos sin excluir comprados o sin comprobar cupo. No presentar recomendación como disponibilidad garantizada. Revisar `parcial`, `historialVerificado` y `disponibilidadVerificada`, además de `fuentesNoDisponibles`. Lista vacía no significa necesariamente falla.

Ejemplo ilustrativo unificado:

```json
{"recomendaciones":[{"idEvento":20,"nombre":"Festival de ejemplo","tipo":"FESTIVAL","fechaHora":"2030-12-20T21:00:00","ciudad":"Tandil","puntuacion":3,"puntuacionPreferencias":3,"compradoresEnComun":0,"motivos":["Coincide con un tipo de evento preferido"],"fuentes":["PREFERENCIAS"]}],"parcial":false,"fuentesNoDisponibles":[],"historialVerificado":true,"disponibilidadVerificada":true}
```

Fuente preferencias (ejemplo ilustrativo):

```json
[{"idEvento":20,"nombre":"Festival de ejemplo","tipo":"FESTIVAL","fechaHora":"2030-12-20T21:00:00","ciudad":"Tandil","puntuacion":3,"motivos":["Coincide con un tipo de evento preferido"]}]
```

Fuente compras (ejemplo ilustrativo, el motivo usa el nombre de un evento de referencia):

```json
[{"idEvento":20,"nombre":"Festival de ejemplo","tipo":"FESTIVAL","fechaHora":"2030-12-20T21:00:00","ciudad":"Tandil","compradoresEnComun":1,"motivo":"Clientes que compraron Recital de ejemplo también compraron Festival de ejemplo"}]
```

## 5. Lugares, artistas y eventos

### Catálogos de lugares y artistas

Sin headers adicionales, query, paginación u orden configurables.

| Método | Ruta | Acción / cuerpo | Éxito | Errores |
| --- | --- | --- | --- | --- |
| GET | `/api/lugares` | Listar; — | 200 Lugar[] | Infraestructura |
| GET | `/api/lugares/{id}` | Obtener; — | 200 Lugar | 404 |
| POST | `/api/lugares` | Crear; LugarRequest | 201 Lugar | Conversión/persistencia |
| PUT | `/api/lugares/{id}` | Reemplazar; LugarRequest | 200 Lugar | 404, persistencia |
| DELETE | `/api/lugares/{id}` | Borrar; — | 204 | 404, referencias pueden causar error no normalizado |
| GET | `/api/artistas` | Listar; — | 200 Artista[] | Infraestructura |
| GET | `/api/artistas/{id}` | Obtener; — | 200 Artista | 404 |
| POST | `/api/artistas` | Crear; ArtistaRequest | 201 Artista | Conversión/persistencia |
| PUT | `/api/artistas/{id}` | Reemplazar; ArtistaRequest | 200 Artista | 404, persistencia |
| DELETE | `/api/artistas/{id}` | Borrar; — | 204 | 404, referencias pueden causar error no normalizado |

Los DTO/controladores de estos catálogos no aplican Bean Validation; los servicios copian campos incluso null. No hay validación de obligatorios, coordenadas o nombres únicos garantizada aquí. La publicación sí exige datos completos del lugar. PUT sustituye todos sus campos.

LugarRequest: `nombre`, `direccion`, `ciudad` textos; `latitud`, `longitud` Double; `tipo`: ESTADIO/TEATRO/SALA/OTRO. Respuesta Lugar agrega `id`. Ejemplos ilustrativos:

```json
{"nombre":"Sala de ejemplo","direccion":"Calle Ejemplo 100","latitud":-37.32,"longitud":-59.13,"ciudad":"Tandil","tipo":"SALA"}
```

```json
{"id":2,"nombre":"Sala de ejemplo","direccion":"Calle Ejemplo 100","latitud":-37.32,"longitud":-59.13,"ciudad":"Tandil","tipo":"SALA"}
```

ArtistaRequest: `{"nombre":"Artista de ejemplo","genero":"Rock"}`. Respuesta Artista ilustrativa: `{"id":3,"nombre":"Artista de ejemplo","genero":"Rock"}`. GET de colección devuelve arrays de esos objetos.

### Eventos: operaciones

Header `O` en esta tabla significa **`X-Organizador-Id: <Long>` obligatorio**, proporcionado por cliente.

| Método | Ruta | Acción / headers / cuerpo | Éxito | Errores |
| --- | --- | --- | --- | --- |
| GET | `/api/eventos` | Listar publicados; — | 200 Evento[] | Infraestructura |
| GET | `/api/eventos/{id}` | Detalle publicado; — | 200 Evento | 404 inexistente/no publicado |
| GET | `/api/eventos/mis-eventos` | Por organizador, incluidos borradores; O; — | 200 Evento[] | Header ausente/inválido |
| GET | `/api/eventos/buscar` | Filtros siguientes; — | 200 Evento[] | 400 orden/tipos; paginación inválida puede dar 500 |
| POST | `/api/eventos` | Crear borrador; EventoRequest | 201 Evento | 404 lugar/artista, datos incompletos pueden dar 500 |
| PUT | `/api/eventos/{id}` | Editar borrador; O; EventoRequest | 200 Evento | 403 organizador, 404 recursos, 409 no borrador |
| PATCH | `/api/eventos/{id}/publicar` | Publicar; O; — | 200 Evento | 400 datos, 403, 404, 409 estado/sectores, 503 inventario |
| PATCH | `/api/eventos/{id}/finalizar` | Finalizar; O; — | 200 Evento | 403, 404, 409 fecha futura/no publicado/cancelado |
| PATCH | `/api/eventos/{id}/suspender` | Suspender publicación; — | 200 Evento | 404, 409 no publicado |
| PATCH | `/api/eventos/{id}/cancelar` | Cancelar sólo en Eventos; — | 200 Evento | 404, 409 finalizado |
| DELETE | `/api/eventos/{id}` | Borrar; — | 204 | 404, persistencia |
| GET | `/api/eventos/interno/{id}` | Consultar aunque no esté publicado; — | 200 Evento | 404 |
| PATCH | `/api/eventos/interno/{id}/disponibilidad` | Query `agotado` boolean obligatoria; — | 204 | 404, query inválida/faltante |

Las últimas dos rutas **están expuestas** por `/api/eventos/**`, pese a llamarse internas. Disponibilidad cambia estado de evento, no cupos: AGOTADO cuando true; vuelve a EN_CURSO si estaba agotado, publicado y futuro cuando false; no modifica cancelados/finalizados.

### Filtros, paginación y orden

GET `G/api/eventos/buscar`:

| Query | Tipo / default | Regla |
| --- | --- | --- |
| `tipo` | Enum opcional | RECITAL/TEATRO/DEPORTE/CONFERENCIA/FESTIVAL/OTRO |
| `idOrganizador`, `idParticipante` | Long opcionales | Igualdad; participante es ID de artista |
| `ciudad` | Texto opcional | Igualdad ignorando mayúsculas; no búsqueda parcial ni normalización de acentos |
| `fechaDesde`, `fechaHasta` | LocalDateTime opcionales | Límites inclusivos |
| `pagina` | int, 0 | Desde cero; usar ≥ 0 |
| `tamanio` | int, 10 | Usar > 0; sin máximo explícito |
| `ordenar` | texto, `fechaHora,asc` | Sólo fechaHora/nombre; asc/desc; sin dirección usa asc |

Combina filtros con AND y exige PUBLICADO. No filtra automáticamente fecha futura, EN_CURSO o cupo. Devuelve array **sin** totalPages/totalElements/hasNext. No hay orden por precio/relevancia ni filtro por nombre. `pagina < 0`/`tamanio <= 0` pueden fallar por PageRequest sin handler específico. No hay validación explícita de coherencia del rango de fechas.

### EventoRequest y estados

Ejemplo ilustrativo para POST/PUT:

```json
{"nombre":"Recital de ejemplo","descripcion":"Descripción de ejemplo","tipo":"RECITAL","idOrganizador":7,"participantesIds":[3],"fechaHora":"2030-12-20T21:00:00","idLugar":2,"demanda":false}
```

Tipos de campos: textos nombre/descripción; tipo enum como búsqueda; IDs Long; participantesIds array de Long; fechaHora local; demanda boolean (omitido → false). También declara `estado` y `estadoPublicacion`, pero el servicio no los toma del cuerpo. Crear fuerza EN_CURSO/BORRADOR. PUT conserva organizador y estados, reemplaza datos/participantes; participantesIds omitido/null vacía participantes. Lugar y artistas referenciados deben existir. No hay @Valid ni constraints en el DTO; idLugar ausente, por ejemplo, puede causar 500 en repositorio en vez de 400.

Publicar exige: nombre/descripción no blancos, tipo, organizador positivo, fecha futura, lugar con nombre/dirección no blancos y latitud/longitud no null, al menos un participante y al menos un sector con precio ≥ 0 y capacidad > 0. No verifica rol del organizador. No exige ciudad o género del artista ni valida rangos de coordenadas.

Estado de evento: EN_CURSO/AGOTADO/CANCELADO/FINALIZADO. Estado de publicación independiente: BORRADOR/PUBLICADO/SUSPENDIDO. Suspender cambia sólo publicación; cancelar cambia a CANCELADO/SUSPENDIDO; finalizar exige publicado y fecha alcanzada. No hay reanudación implementada para SUSPENDIDO: publicar sólo admite BORRADOR. Finalización no es automática.

Respuesta Evento ilustrativa (misma forma en detalle, creación, edición y transiciones; listas contienen objetos iguales):

```json
{"id":10,"nombre":"Recital de ejemplo","descripcion":"Descripción de ejemplo","tipo":"RECITAL","idOrganizador":7,"participantes":[{"id":3,"nombre":"Artista de ejemplo","genero":"Rock"}],"fechaHora":"2030-12-20T21:00:00","lugar":{"id":2,"nombre":"Sala de ejemplo","direccion":"Calle Ejemplo 100","latitud":-37.32,"longitud":-59.13,"ciudad":"Tandil","tipo":"SALA"},"estado":"EN_CURSO","estadoPublicacion":"BORRADOR","demanda":false}
```

Un borrador recién creado no se obtiene por GET público individual; usar mis-eventos para gestión. Evento no contiene precio/disponibilidad: consultar inventario.

## 6. Sectores, asientos e inventario

Sin headers adicionales. IDs y filtros de IDs positivos. Sin paginación/orden configurables.

| Método | Ruta | Acción / cuerpo / query | Éxito | Errores |
| --- | --- | --- | --- | --- |
| GET | `/sectores` | Listar; query opcional idLugar; — | 200 Sector[] | 400 filtro |
| GET | `/sectores/{id}` | Obtener; — | 200 Sector | 400, 404 |
| POST | `/sectores` | Crear; SectorRequest | 201 Sector y Location | 400, 409 integridad |
| PUT | `/sectores/{id}` | Reemplazar; SectorRequest | 200 Sector | 400, 404, 409 asientos/eventos asociados |
| DELETE | `/sectores/{id}` | Borrar; — | 204 | 400, 404, 409 referencias |
| GET | `/asientos` | Listar físicos; query opcional idSector; — | 200 Asiento[] | 400 filtro |
| GET | `/asientos/{id}` | Obtener físico; — | 200 Asiento | 400, 404 |
| POST | `/asientos` | Crear; AsientoRequest | 201 Asiento y Location | 400, 404 sector, 409 tipo/capacidad/duplicado/configuración |
| PUT | `/asientos/{id}` | Editar; AsientoRequest | 200 Asiento | 400, 404, 409 traslado/duplicado/configuración |
| DELETE | `/asientos/{id}` | Borrar; — | 204 | 400, 404, 409 referencias/tipo |
| PUT | `/eventos/{idEvento}/inventario` | Inicializar; InventarioRequest | 204 | 400, 404 evento, 409 política/cancelado, 502 datos remotos, 503 dependencia |
| PUT | `/eventos/{idEvento}/sectores` | Configurar; SectorEventoRequest | 200 Disponibilidad | 400, 404 inventario/sector, 409 estado/lugar/capacidad/configuración |
| GET | `/eventos/{idEvento}/sectores` | Disponibilidad; — | 200 Disponibilidad[] | 400, 404 inventario |
| GET | `/eventos/{idEvento}/sectores/{idSector}/disponibilidad` | Disponibilidad individual; — | 200 Disponibilidad | 400, 404 |
| POST | `/eventos/{idEvento}/inventario/cancelacion` | Cancelar inventario; — | 204 | 400, 404 |

GET de disponibilidad devuelve Cache-Control: no-store y aplica vencimientos antes de calcular cupos. Un filtro físico sin coincidencias da array vacío. Asientos físicos no contienen estado/disponibilidad de un evento. Cancelar inventario no reintegra ni cambia entradas en Compras.

### Cuerpos y reglas

- SectorRequest, todos obligatorios: `idLugar` positivo (no consulta existencia externa), `nombre` no blanco máximo 255 (se guarda trim), `tipo` CAMPO/NUMERADO, `capacidadTotal` entero positivo. Ejemplo: `{"idLugar":2,"nombre":"Platea","tipo":"NUMERADO","capacidadTotal":1}`.
- AsientoRequest, todos obligatorios: `idSector` positivo existente, `fila` y `columna` textos no blancos máximo 255, se guardan trim. Ejemplo: `{"idSector":4,"fila":"A","columna":"1"}`. Sólo NUMERADO; sin duplicados por sector/fila/columna ni exceso de capacidad. No permite traslado de sector. No permite alterar distribución de sector ya asociado a eventos. Sector con asientos/eventos tampoco admite edición/baja. No hay borrado en cascada.
- InventarioRequest: `{"limitePorCliente":4}`, entero positivo obligatorio. Consulta lugar/fecha de Eventos. Repetir mismo límite es seguro; cambiar política o usar cancelado da 409.
- SectorEventoRequest: `{"idSector":4,"precio":15000.00,"capacidadHabilitada":1}`, todos obligatorios. ID positivo, precio ≥ 0 (10 dígitos enteros, máximo 2 decimales), capacidad positiva ≤ física. Sector debe pertenecer al lugar del evento. NUMERADO exige cantidad de asientos creados igual a capacidad habilitada. Configuración idéntica es repetible; cambiar precio/capacidad ya configurados da 409. No hay desconfiguración.

Respuestas ilustrativas Sector: `{"id":4,"idLugar":2,"nombre":"Platea","tipo":"NUMERADO","capacidadTotal":1}`; Asiento: `{"id":5,"idSector":4,"fila":"A","columna":"1"}`.

Disponibilidad ilustrativa:

```json
{"idEvento":10,"idSector":4,"tipo":"NUMERADO","precio":15000.00,"capacidad":1,"disponibles":1,"reservados":0,"vendidos":0,"cancelado":false,"asientos":[{"idAsiento":5,"fila":"A","columna":"1","estado":"DISPONIBLE"}]}
```

Estados de posición por evento: DISPONIBLE/RESERVADO/VENDIDO. CAMPO devuelve asientos vacíos. Inventario cancelado informa disponibles=0. Disponibilidad es una fotografía, no garantía de que la próxima reserva se acepte.

## 7. Reservas

IDs positivos, sin query/headers adicionales. Idempotencia en JSON, no en header. Algunas operaciones son de coordinación utilizadas por Compras, pero están expuestas.

| Método | Ruta | Acción / cuerpo | Éxito | Errores |
| --- | --- | --- | --- | --- |
| POST | `/reservas` | Crear/recuperar; ReservaRequest | **200** Reserva, no-store | 400, 404 recursos, 409 clave/cupo/límite/asientos/venta, 502 datos remotos, 503 dependencia/bloqueo |
| GET | `/reservas/{id}` | Consultar/aplicar vencimiento; — | 200 Reserva, no-store | 400, 404 |
| POST | `/reservas/{id}/confirmacion` | Confirmar; — | 200 Reserva | 400, 404, 409 estado/venta, 502, 503 |
| POST | `/reservas/{id}/liberacion` | Liberar activa; — | 200 Reserva | 400, 404, 409 venta confirmada/devuelta |
| POST | `/reservas/{id}/devoluciones` | Devolver cupo vendido; DevolucionInventarioRequest | 200 Reserva | 400, 404, 409 estado/plazo/clave/asientos/cantidad, 502, 503 |

ReservaRequest ilustrativo, todos obligatorios:

```json
{"claveIdempotencia":"reserva-ejemplo-001","idCliente":7,"idEvento":10,"idSector":4,"cantidad":1,"idAsientos":[5]}
```

Clave no blanca máximo 100; IDs positivos; cantidad 1–1000; lista no null máximo 1000 IDs positivos no nulos ni duplicados. NUMERADO exige exactamente cantidad de asientos del sector/evento, todos disponibles; CAMPO exige `idAsientos: []`.

Límite por cliente/evento cuenta reservas ACTIVA y ventas CONFIRMADA menos devoluciones entre todos los sectores. Para reservar/confirmar exige evento publicado, futuro, no cancelado/finalizado/agotado, mismo lugar que inventario. Clave repetida con mismo cliente/evento/sector/cantidad/conjunto de asientos recupera reserva sin renovar plazo, incluso si cambió de estado; contenido distinto da 409.

DevolucionInventarioRequest ilustrativo, todos obligatorios:

```json
{"claveIdempotencia":"devolucion-ejemplo-001","cantidad":1,"solicitadaEn":"2030-12-01T15:00:00Z","idAsientos":[5]}
```

Clave no blanca máximo 100; cantidad positiva; instante obligatorio; array no null de hasta 1000 IDs positivos sin duplicados. Exige CONFIRMADA e inventario no cancelado, cantidad ≤ venta pendiente; en NUMERADO son asientos propios no devueltos y cantidad coincide; CAMPO lista vacía. Fecha entre creación de reserva y presente, como máximo inicio del evento menos 72 h (consulta fecha actual de Eventos). Reintento compara reserva/cantidad/asientos/fecha truncada a microsegundos. No mueve dinero ni actualiza entradas/solicitudes de Compras.

Reserva, respuesta ilustrativa:

```json
{"id":30,"claveIdempotencia":"reserva-ejemplo-001","idCliente":7,"idEvento":10,"idSector":4,"cantidad":1,"precioUnitario":15000.00,"venceEn":"2030-12-01T15:10:00Z","estado":"ACTIVA","cantidadDevuelta":0,"idAsientos":[5],"idAsientosDevueltos":[]}
```

Estados: ACTIVA/CONFIRMADA/LIBERADA/DEVUELTA/VENCIDA. Reserva dura **10 minutos** configurados; barrido con demora fija **30000 ms** entre ejecuciones. Consultas y operaciones también aplican vencimientos; a la hora exacta ya vence. Usar `venceEn` del servidor como autoridad.

Confirmar vencida puede devolver **200 con VENCIDA**. Confirmación ya hecha es repetible; liberar vencida/liberada no descuenta de nuevo. Devolución parcial mantiene CONFIRMADA; total → DEVUELTA. No hay renovación de reserva ni lista general de reservas.

## 8. Compra, pago, entradas y QR

Sin headers obligatorios adicionales. Compras/Entradas/Solicitudes aceptan opcionalmente `X-Correlation-Id` seguro (`[A-Za-z0-9._-]{1,100}`); de lo contrario generan otro. Lo devuelven y Compras lo propaga a Feign.

| Método | Ruta | Acción / cuerpo / query | Éxito | Errores |
| --- | --- | --- | --- | --- |
| POST | `/compras` | Crear compra y reserva; CompraRequest | 201 Compra incluso por reintento | 400 validación/cantidad, 404 recursos, 409 clave/venta/cupo/reserva, 502, 503 |
| GET | `/compras/{id}` | Obtener; — | 200 Compra | 404 |
| GET | `/compras/cliente/{idCliente}` | Listar, ID descendente; — | 200 Compra[] | Infraestructura |
| PUT | `/compras/{id}/pago` | Query `aprobado` boolean obligatoria; — | 200 Compra | 400 query/validación, 404, 409 resultado/venta, 502, 503 |
| POST | `/compras/eventos/{idEvento}/cancelacion` | Cancelación coordinada; — | 200 CancelacionEvento | 404, 409, 502, 503 |
| GET | `/entradas/{id}` | Obtener; — | 200 Entrada | 404 |
| GET | `/entradas/qr/{qr}` | Consultar por QR; — | 200 Entrada | 404 |
| GET | `/entradas/compra/{idCompra}` | Listar emitidas; — | 200 Entrada[] | Infraestructura |
| GET | `/entradas/cliente/{idCliente}` | Listar por cliente; — | 200 Entrada[] | Infraestructura |
| PATCH | `/entradas/{id}/uso` | Consumir entrada; — | 200 Entrada | 404, 409 no vigente/usada |
| PATCH | `/entradas/qr/{qr}/uso` | Consumir por QR; — | 200 Entrada | 404, 409 no vigente/usada |

Sin paginación/orden configurables ni query adicional. QR es texto obligatorio de ruta: usar encodeURIComponent. No hay POST público de entrada ni GET general de compras.

### CompraRequest y pago

Ejemplo ilustrativo NUMERADO:

```json
{"claveIdempotencia":"compra-ejemplo-001","idCliente":7,"idEvento":10,"idSector":4,"idsAsientos":[5],"medioDePago":"SIMULADO"}
```

CAMPO: sustituir idsAsientos por `"cantidadEntradas":2`. Obligatorios clave no blanca máximo 100, IDs positivos y medioDePago no blanco. Medio no es enum/catálogo validado; SIMULADO es un texto ilustrativo. Debe indicar idsAsientos no vacío o cantidadEntradas positiva. Asientos positivos no nulos. Si ambos están, cantidad efectiva = tamaño de idsAsientos; una cantidad explícita no positiva igualmente viola validación. Máximo efectivo 1000. Inventario rechaza duplicados.

No recibe precio, importe, idReserva, QR o descripción. Crea/recupera reserva con la misma clave y calcula total. No existe endpoint para adjuntar a compra una reserva independiente. Reintentar con mismo cliente/evento/sector/cantidad efectiva/medio/conjunto de asientos recupera compra; distinto contenido → 409.

Respuesta Compra ilustrativa pendiente:

```json
{"id":40,"idCliente":7,"idEvento":10,"idSector":4,"cantidadEntradas":1,"medioDePago":"SIMULADO","descripcion":"Compra pendiente de pago","estadoCompra":"PENDIENTE","claveIdempotencia":"compra-ejemplo-001","idReserva":30,"precioUnitario":15000.00,"importeTotal":15000.00,"referenciaPago":null,"referenciaReintegro":null,"estadoReintegro":null,"idsAsientos":[5]}
```

Estados compra: PENDIENTE/APROBADA/RECHAZADA/EXPIRADA/CANCELADA. Pago es **simulado**, aprobado según boolean del request: no hay tarjeta, checkout de proveedor, webhook ni pago real. Referencias generadas con prefijos SIM-/SIM-REF-. No hay moneda implementada.

Pago aprobado confirma reserva y emite una entrada por lugar si CONFIRMADA. Si devuelve VENCIDA, reintegra simulado y compra EXPIRADA, sin entradas, aun con HTTP 200. Rechazo libera y deja RECHAZADA. Aprobación repetida sobre APROBADA/EXPIRADA y rechazo repetido sobre RECHAZADA recuperan resultado; decisión incompatible da 409. Revalida evento vendible antes de procesar pendiente, incluso un rechazo.

Compra no incluye venceEn: consultar reserva por idReserva. GET compra no sincroniza expiración: una abandonada puede permanecer PENDIENTE con reserva VENCIDA. No hay cancelación individual/DELETE de compra ni renovación. No cambiar clave por timeout; para una operación nueva tras expiración usar otra clave con conocimiento del resultado anterior.

### Entradas y acceso

Entrada ilustrativa:

```json
{"id":50,"qr":"00000000-0000-4000-8000-000000000001","estadoQr":"NO_ESCANEADO","idCliente":7,"idEvento":10,"idSector":4,"idAsiento":5,"precio":15000.00,"estadoEntrada":"PAGA"}
```

QR es cadena UUID, no imagen/PDF/payload firmado. CAMPO tiene idAsiento null. La respuesta no contiene idCompra. Estados QR: NO_ESCANEADO/ESCANEADO. Estados entrada: PAGA/SOLICITUD_DEVOLUCION/DEVOLUCION_ACEPTADA/DEVOLUCION_RECHAZADA/EVENTO_CANCELADO.

GET por QR no consume ni autoriza acceso. PATCH uso exige exactamente PAGA y no escaneada; luego marca ESCANEADO. No verifica fecha/evento remoto/evento elegido ni rol PERSONAL_ACCESO. No hay bloqueo/versionado explícito en esa operación que garantice exclusión de dos escaneos simultáneos.

## 9. Devoluciones y cancelación

Headers comunes y correlation opcional; sin query ni paginación. IDs de ruta obligatorios.

| Método | Ruta | Acción / cuerpo | Éxito | Errores |
| --- | --- | --- | --- | --- |
| POST | `/solicitudes` | Solicitar devolución; SolicitudRequest | 201 Solicitud | 400, 403 cliente ajeno, 404 entrada/evento, 409 plazo/estado/duplicada, 502, 503 |
| GET | `/solicitudes/{id}` | Obtener; — | 200 Solicitud | 404 |
| GET | `/solicitudes/entrada/{idEntrada}` | Historial, ID ascendente; — | 200 Solicitud[] | Infraestructura |
| GET | `/solicitudes/pendientes` | Pendientes, ID ascendente; — | 200 Solicitud[] | Infraestructura |
| GET | `/solicitudes/cliente/{idCliente}` | Por cliente, ID ascendente; — | 200 Solicitud[] | Infraestructura |
| PUT | `/solicitudes/{id}/resolver` | Resolver; SolicitudResolucion | 200 Solicitud | 400 estado/DTO, 404, 409 decisión/entrada/inventario, 502, 503 |

SolicitudRequest ilustrativo: `{"idEntrada":50,"idCliente":7,"descripcion":"No podré asistir"}`. Obligatorios IDs positivos, descripción no blanca sin máximo explícito en DTO. Entrada propia del cliente indicado, PAGA o DEVOLUCION_RECHAZADA, sin solicitud PENDIENTE/ACEPTADA. Evento no CANCELADO/FINALIZADO y al menos **72 h** antes de inicio. No excluye explícitamente QR ya escaneado. No rechaza sólo por publicación suspendida.

Crear deja solicitud PENDIENTE y entrada SOLICITUD_DEVOLUCION: no libera ni reintegra. SolicitudResolucion ilustrativa: `{"idAdministrador":8,"estadoSolicitud":"ACEPTADA"}`. Ambos obligatorios; administrador positivo, sin verificar rol. Sólo acepta ACEPTADA/RECHAZADA aunque enum contenga PENDIENTE/CANCELADA. Igual decisión repetida devuelve resultado; distinta → 409.

Aceptar libera una unidad con fecha original de solicitud y reintegra precio simulado; entrada DEVOLUCION_ACEPTADA. Rechazar deja DEVOLUCION_RECHAZADA. **Inconsistencia:** uso QR sólo admite PAGA, por lo que una devolución rechazada impide usar la entrada mediante esos endpoints; no documentar como válida para acceso.

Solicitud ilustrativa recién creada:

```json
{"id":60,"idEntrada":50,"idCompra":40,"idCliente":7,"idAdministrador":null,"fechaSolicitud":"2030-12-01T15:00:00Z","fechaResolucion":null,"referenciaReintegro":null,"estadoReintegro":null,"descripcion":"No podré asistir","estadoSolicitud":"PENDIENTE"}
```

Tras resolver se completan administrador/fecha y estado; aceptación completa referencia y reintegro COMPLETADO con el simulador. Enum reintegro PENDIENTE/COMPLETADO; no hay webhook/workflow financiero asíncrono que complete PENDIENTE. No hay SLA de resolución de solicitud.

### Cancelación completa

`POST G/compras/eventos/{idEvento}/cancelacion` sin cuerpo: cancela primero evento, luego compras PENDIENTE/APROBADA, libera pendientes, reintegra entradas no devueltas/canceladas, marca EVENTO_CANCELADO, cancela solicitudes pendientes afectadas y cancela inventario. Compras quedan CANCELADA. No exige administrador autenticado.

Respuesta ilustrativa:

```json
{"idEvento":10,"comprasCanceladas":1,"entradasReintegradas":1,"importeReintegrado":15000.00,"inventarioCancelado":true}
```

Contadores corresponden a esa ejecución, no total histórico. Repetición puede devolver ceros. Si no hay inventario puede responder éxito con inventarioCancelado=false. PATCH cancelar de Eventos sólo cambia catálogo; cancelación de Inventario sólo libera lugares. Ninguno reemplaza el flujo coordinado.

## 10. Orden de peticiones y comportamiento de interfaz

### Perfil

1. GET roles, seleccionar ID existente, POST usuarios. Guardar ID como perfil de demostración, no como credencial.
2. PUT preferencias reemplaza ambos campos; GET preferencias recupera también IDs favoritos.
3. GET catálogo artistas, PUT/DELETE favorito por ID; GET favoritos resuelve sus datos.

### Publicación

1. Crear lugar/artistas, sector físico y todos los asientos si numerado antes de asociarlo a eventos.
2. POST evento → BORRADOR; gestionar por mis-eventos con X-Organizador-Id.
3. PUT inventario con límite, luego PUT sectores del evento con precio/capacidad.
4. PATCH publicar con X-Organizador-Id; si falla, conservar edición y error real. GET catálogo/detalle público una vez publicado.
5. Suspensión detiene venta sin reintegrar; finalizar es manual y exige fecha alcanzada; cancelación completa se hace en Compras.

### Compra y pago

1. GET eventos/buscar, detalle público, disponibilidad por evento/sector. Publicado no significa comprable.
2. Seleccionar IDs físicos o cantidad de campo. Generar y conservar una clave y cuerpo por intento.
3. POST compras crea compra y reserva. **No crear previamente una reserva independiente:** no hay asociación posterior implementada.
4. GET reserva usando idReserva para contador venceEn y estado. No hay renovación ni WebSocket/SSE. Elegir polling según UI, sin suponer intervalo obligatorio del contrato.
5. PUT pago?aprobado=true/false en demostración; revisar estadoCompra aun con 200. Sólo APROBADA → GET entradas/compra/{id} y mostrar entradas. EXPIRADA/RECHAZADA no emiten entradas.
6. 409 de cupo → recargar disponibilidad y permitir selección. Timeout/5xx → no afirmar éxito/fracaso definitivo; recuperar compra con misma clave/cuerpo o GET por ID conocido y consultar reserva.

Liberar directamente reserva activa no cancela la compra automáticamente. Al abandonar, el vencimiento libera cupo; no hay cancelación individual de compra. Contador local no es autoridad.

### Devolución

1. Consultar entradas/cliente y fecha del evento; POST solicitud por **una entrada**.
2. Mostrar PENDIENTE, sin reintegro. Consultar solicitud o lista por cliente para evolución; no hay push conectado a este flujo.
3. Gestión: GET pendientes y PUT resolver. Recargar entrada/reserva tras aceptación; revisar limitación de QR tras rechazo.

### Cancelación e ingreso

Cancelar desde UI mediante POST compras/eventos/{id}/cancelacion; recargar evento, compras, entradas, solicitudes. No hay transacción distribuida ni progreso consultable: un fallo intermedio puede dejar cambios remotos aplicados; reconciliar lecturas y reintentar la misma operación con cautela.

Ingreso: leer cadena QR, opcional GET para previsualizar; PATCH qr/{qr}/uso para registrar consumo. 200/ESCANEADO aceptado; 404 desconocido; 409 usado/no vigente. No permitir ingreso sólo por GET ni por validación local.

### Tiempos y funciones ausentes

Feign relevante configura 3000 ms conexión y 5000 ms lectura por llamada. Compras usa Retryer.Default(200,500,2) para fallos reintentables. Una operación encadena varias llamadas: 5 s no es timeout global ni SLA. Abortar fetch no cancela necesariamente trabajo servidor. Inventario ocupado puede dar 503/Retry-After: 1; conservar clave al reintentar.

No hay fila virtual, turnos, posición/espera estimada, token de admisión o renovación de reserva. `demanda` sólo persiste boolean en evento. No hay transferencia de entradas, carrito multievento, proveedor de pago real o descarga PDF implementados por estos controladores.

## 11. Errores y manejo en frontend

### Compras, Entradas y Solicitudes

ApiExceptionHandler devuelve `error`, `detalle` nullable, `correlationId`, `timestamp`. Ejemplo ilustrativo:

```json
{"error":"La entrada ya fue utilizada","detalle":null,"correlationId":"ejemplo-001","timestamp":"2030-12-01T15:00:00Z"}
```

IllegalArgumentException → 400; IllegalStateException → 409; validación, JSON ilegible/query requerida faltante → 400; integridad → 409; ResponseStatusException conserva status; Feign reintentable/conexión → 503; Feign remoto 4xx conserva status con mensaje genérico; otros Feign → 502; no controladas → 500. Conversión de IDs/query no está normalizada completamente y puede terminar en 500.

### Usuarios, Sectores e Inventario

Usuarios usa ResponseStatusException y ProblemDetail para integridad/concurrencia (409). Sectores/Inventario manejan constraints como 400, integridad como 409 y bloqueo pesimista como 503 con Retry-After: 1. Ejemplo ilustrativo ProblemDetail:

```json
{"type":"about:blank","title":"Service Unavailable","status":503,"detail":"Inventario ocupado; reintentar con la misma clave"}
```

No todos los errores tienen la misma estructura ni incluyen detail/mensaje. Eventos no tiene advice propio y conserva manejo del framework; errores de persistencia/argumentos no controlados pueden dar 500. Configura server.error.include-message=always. Gateway no define esquema uniforme para fallo de ruta/conexión/método. No asumir JSON ni texto exacto en todos los errores.

Distinguir HTTP y negocio: 200 VENCIDA, EXPIRADA, RECHAZADA o parcial no implica compra exitosa. No reintentar indiscriminadamente crear solicitudes, usar QR o borrar datos. Si se perdió respuesta, consultar antes de repetir operaciones sin garantía de idempotencia.

## 12. Ejemplos JavaScript con fetch

Ejemplos ilustrativos, no ejecutados contra la API. `globalThis.TICKETYA_GATEWAY_URL` es configuración propuesta **del frontend**, no variable del backend. IDs deben obtenerse de respuestas reales.

```javascript
const GATEWAY_URL = (globalThis.TICKETYA_GATEWAY_URL ?? "http://localhost:8080").replace(/\/+$/, "");

class ApiError extends Error {
  constructor(response, payload) {
    super((typeof payload === "string" ? payload : payload?.detail ?? payload?.error ?? payload?.title)
      || `HTTP ${response.status}`);
    this.status = response.status;
    this.payload = payload;
    this.correlationId = response.headers.get("X-Correlation-Id") ?? payload?.correlationId;
    this.retryAfter = response.headers.get("Retry-After");
  }
}

async function api(path, { method = "GET", body, token, headers = {}, signal } = {}) {
  const h = new Headers(headers);
  h.set("Accept", "application/json");
  if (body !== undefined) h.set("Content-Type", "application/json");
  // Sólo ilustra el envío convencional para una futura autenticación.
  // El backend actual no emite ni valida JWT. No enviar token por defecto.
  if (token) h.set("Authorization", `Bearer ${token}`);
  const response = await fetch(`${GATEWAY_URL}${path}`, {
    method, headers: h, signal,
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const text = response.status === 204 ? "" : await response.text();
  let data = text || null;
  if (text) { try { data = JSON.parse(text); } catch { /* Puede ser error de proxy en texto. */ } }
  if (!response.ok) throw new ApiError(response, data);
  return { data, headers: response.headers, status: response.status };
}

async function buscarEventos() {
  const q = new URLSearchParams({ tipo: "RECITAL", ciudad: "Tandil", pagina: "0", tamanio: "10", ordenar: "fechaHora,asc" });
  return (await api(`/api/eventos/buscar?${q}`)).data;
}

async function publicarEvento(id, idOrganizador) {
  return (await api(`/api/eventos/${id}/publicar`, {
    method: "PATCH", headers: { "X-Organizador-Id": String(idOrganizador) }
  })).data;
}
```

Compra numerada y pago de demostración:

```javascript
function nuevoIntento(idCliente, idEvento, idSector, idsAsientos) {
  return { claveIdempotencia: crypto.randomUUID(), idCliente, idEvento, idSector,
    idsAsientos, medioDePago: "SIMULADO" };
}

async function crearORecuperarCompra(intento) {
  // Conservar este objeto fuera de la función y reutilizarlo tras timeout.
  const { data: compra } = await api("/compras", { method: "POST", body: intento });
  const { data: reserva } = await api(`/reservas/${compra.idReserva}`);
  return { compra, reserva }; // UI: reserva.venceEn y estado, no contador fijo.
}

async function pagarDemo(idCompra, aprobado) {
  const q = new URLSearchParams({ aprobado: String(aprobado) });
  const { data: compra } = await api(`/compras/${idCompra}/pago?${q}`, { method: "PUT" });
  const entradas = compra.estadoCompra === "APROBADA"
    ? (await api(`/entradas/compra/${compra.id}`)).data : [];
  return { compra, entradas }; // Revisar EXPIRADA/RECHAZADA aun con 200.
}
```

CAMPO usa cantidadEntradas en lugar de selección numerada. No generar una nueva clave dentro de un bloque de reintento ni enviar precio desde frontend.

Devolución, QR y errores:

```javascript
async function solicitarDevolucion(idEntrada, idCliente, descripcion) {
  return (await api("/solicitudes", {
    method: "POST", body: { idEntrada, idCliente, descripcion }
  })).data;
}

async function resolverDevolucion(id, idAdministrador, aceptar) {
  return (await api(`/solicitudes/${id}/resolver`, {
    method: "PUT", body: { idAdministrador, estadoSolicitud: aceptar ? "ACEPTADA" : "RECHAZADA" }
  })).data;
}

async function usarQr(qr, mostrarMensaje) {
  try {
    const { data } = await api(`/entradas/qr/${encodeURIComponent(qr)}/uso`, { method: "PATCH" });
    mostrarMensaje(`Uso registrado: ${data.estadoQr}`);
  } catch (error) {
    if (error instanceof ApiError) {
      mostrarMensaje(error.status === 404 ? "Entrada no encontrada" : error.message);
      // Conservar correlationId para diagnóstico; no imprimir datos sensibles.
    } else {
      mostrarMensaje("No se pudo leer la respuesta. Revisar conexión y CORS antes de reintentar.");
    }
  }
}
```

Error de red/CORS rechaza fetch sin Response y sin status HTTP accesible. Un 503 puede indicar espera Retry-After, pero no implica reintentar cualquier acción automáticamente. El helper admite `token` sólo para ilustrar Authorization; no existe login/refresh implementado que permita completarlo hoy.

## 13. Endpoints no publicados y notificaciones

### Contratos internos existentes no enrutados

| Método | Ruta del servicio (no agregar G esperando que funcione) | Servicio |
| --- | --- | --- |
| GET | `/interno/recomendaciones/clientes/{idCliente}` | Compras, agregados |
| GET | `/interno/recomendaciones/clientes/{idCliente}/historial-valido` | Compras, IDs propios |
| POST | `/interno/eventos/recomendaciones/candidatos` | Eventos |
| POST | `/interno/eventos/recomendaciones/por-ids` | Eventos |
| POST | `/interno/inventario/disponibilidad` | SectorAsiento |
| POST | `/api/sucesos` | Notificaciones, registrar suceso |
| GET | `/api/notificaciones/clientes/{idCliente}` | Notificaciones, últimas 100 por fecha descendente |
| GET | `/api/bitacora` | Notificaciones, query obligatoria idEvento |
| GET | `/api/reportes/eventos/{idEvento}` | Notificaciones, agregados de sucesos |
| POST | `/api/logs` | Notificaciones, registrar log |
| GET | `/api/logs` | Notificaciones, desde/hasta obligatorios (Instant), servicio opcional, hasta 500 resultados por fecha descendente |

Usuarios consume los cinco contratos internos de recomendaciones por Feign. Frontend usa `/usuarios/{id}/recomendaciones...`.

El checkout incluye `msv-notificaciones`, puerto por defecto **8006**, pero el Gateway no contiene rutas para él y el Compose raíz no lo incorpora como aplicación. Tiene configuración/Compose propios. No presentar sus URLs como disponibles desde G ni incorporarlas a un flujo frontend como dependencia ya conectada.

En Notificaciones, POST sucesos persiste suceso con clave idempotente y publica evento local; tras commit un listener asíncrono crea registro de notificación. Puede guardar estado OMITIDA sin destinatario o ENVIADA con plantilla para COMPRA_CONFIRMADA, DEVOLUCION_APROBADA, EVENTO_CANCELADO. **ENVIADA en ese código no prueba envío de email externo**: el listener guarda un registro y no invoca un proveedor de correo. No se encontraron productores de esos sucesos desde Compras/Eventos en los servicios revisados. Por tanto, no afirmar que una compra o cancelación dispara automáticamente este módulo. Reportes calculan a partir de sucesos registrados, no directamente de ventas/inventario.

No hay ruta Gateway para `/openapi.json` de cada servicio ni OpenAPI agregada. Actuator de proveedores no se reenvía. El Gateway incluye Actuator y Compose consulta `GET G/actuator/health`; es salud operativa, no garantía de que todos los flujos/dependencias funcionen. Forma mínima ilustrativa: `{"status":"UP"}`.

## 14. Pendientes y diferencias que afectan la integración

| Hallazgo | Consecuencia |
| --- | --- |
| JWT/login/refresh/roles sin implementar | No hay inicio de sesión real ni autorización basada en identidad |
| CORS sin configurar | Conexión directa desde otro origen pendiente de configuración/proxy |
| Rutas internas de Eventos y mutaciones de Reservas sí publicadas | El nombre interno no protege; deben restringirse en backend si se requiere |
| Estados declarados en EventoRequest ignorados; organizador no se cambia en PUT | Usar transiciones reales, no enviar estados esperando aplicarlos |
| Lugares/Artistas/Eventos sin validación uniforme | Campos incompletos pueden persistirse o fallar con 500 |
| Búsqueda sin precio/relevancia/totales | No ofrecerlos como capacidades existentes aunque aparezcan en historias |
| Catálogo filtra publicación, no comprabilidad | Consultar disponibilidad y respetar rechazo al comprar |
| Compra usa idsAsientos/cantidadEntradas; Reserva usa idAsientos/cantidad | Adaptar cuerpos; no copiar DTO entre endpoints |
| Compra no devuelve venceEn | Consultar reserva para cuenta regresiva |
| Usuario ciudadPreferida vacía es cadena; Preferencias usa null | Normalizar visualmente sin inventar contrato único |
| Catálogo artista usa id; favoritos usa idArtista | Adaptar mapeo; altas de favoritos no validan catálogo |
| DEVOLUCION_RECHAZADA no cumple PAGA requerido por uso QR | Requiere corrección backend para permitir ingreso tras rechazo |
| Uso QR no valida fecha/evento/rol ni bloquea escaneos concurrentes explícitamente | No prometer control de acceso completo |
| Política/configuración inventario inmutable salvo repetición idéntica | No ofrecer cambiar precio/cupo/límite o quitar sector configurado |
| Borrador puede cambiar lugar después de inicializar inventario | Reserva detecta diferencia; no hay reconstrucción automática |
| Sector no verifica idLugar externo al crearse | Alta física no demuestra existencia del lugar |
| Cancelar catálogo/inventario o borrar evento no compensa todo | Usar cancelación coordinada de Compras |
| Sin transacción distribuida | Fallas entre servicios pueden dejar cambios parciales; reconciliar antes de declarar rollback |
| Compra PENDIENTE puede tener reserva VENCIDA | Consultar ambos estados; no hay sincronización automática al leer compra |
| Pago/reintegro simulados, sin moneda/catálogo de medios | No presentar cobro real ni confirmación por webhook inexistente |
| Notificaciones presente pero no en Gateway ni conectada a productores de compra/evento | No prometer avisos automáticos ni reportes de ventas completos |
| demanda sin fila virtual | No hay turnos/posición/espera consultables |

El OpenAPI estático de Eventos contiene una descripción que dice que el catálogo no está en Gateway; `application.yml` sí lo enruta y es la fuente priorizada aquí. Los tests de rutas del Gateway usan backends simulados: un test de transporte de un método/ruta no demuestra que el controlador real lo implemente. Por ejemplo, inventario se configura mediante PUT, no GET.

Las historias de usuario son objetivos, no evidencia de implementación: orden por precio/relevancia, validez de entrada tras rechazo y avisos automáticos no se deben inferir de ellas.

## 15. Fuentes para mantener esta guía

- `api-gateway/src/main/resources/application.yml`, `api-gateway/pom.xml`, `docker-compose.yml`: conectividad y publicación.
- `msv-usuario/src/main/java/com/msvusuario/`: controller, dto, entity, service, repository, config; preferencias, favoritos, roles, recomendaciones y reloj.
- `msv-evento/src/main/java/msvevento/`: controller, dto, entity, service, repository y client; catálogo, publicación, búsquedas y cambios de estado.
- `msv-SectoerAsiento/src/main/java/com/universidad/msvsectoerasiento/`: controller, dto, enums, service, repository, client, config; inventario, validaciones, reservas, barrido y devoluciones.
- `msv-compraEntardaSol/src/main/java/org/msvcompraentradasol/`: controllers, dtos, enums, services, repositories, feigns y config; pago simulado, QR, devoluciones, errores y correlación.
- `msv-notificaciones/src/main/java/com/msvnotificaciones/`: controller, dto, entity, repository, service y config; sucesos, notificaciones persistidas, logs y reportes no publicados.
- `src/main/resources/application.properties` de cada módulo: puertos, URLs Feign y tiempos. Los REST.md, OpenAPI, Postman y tests complementan la lectura, pero no sustituyen el código.

La generación de este archivo no requiere cambios en controladores, servicios, DTO ni configuración.
