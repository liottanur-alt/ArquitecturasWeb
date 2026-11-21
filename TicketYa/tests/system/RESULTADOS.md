# Resultados vigentes - 21/11/2026

## Ultima verificacion: regresion Java y auditoria de sistema (21/11/2026)

Se repitieron las cinco suites Java completas con `.\mvnw.cmd test` y Java 21. El resultado final fue **368 pruebas, 0 fallos, 0 errores y 0 omitidas**, con los cinco modulos en `BUILD SUCCESS`:

| Modulo | Pruebas | Resultado |
| --- | ---: | --- |
| `msv-usuario` | 182 | 0 fallos, 0 errores, 0 omitidas |
| `api-gateway` | 21 | 0 fallos, 0 errores, 0 omitidas |
| `msv-evento` | 23 | 0 fallos, 0 errores, 0 omitidas |
| `msv-SectoerAsiento` | 49 | 0 fallos, 0 errores, 0 omitidas |
| `msv-compraEntardaSol` | 93 | 0 fallos, 0 errores, 0 omitidas |

Usuarios se ejecuto contra MongoDB en `127.0.0.1:27017` con una base temporal exclusiva informada por `TEST_MONGODB_URI` y el secreto de prueba del perfil `test`, sin tocar la base `usuarios`. Eventos uso Redis via Testcontainers. Los demas modulos usaron H2 y proveedores HTTP simulados. Los informes surefire se generaron el 21/11 entre las 11:50 y 11:57. Esta corrida confirma el ultimo cambio de `SolicitudService` (la identidad del cliente se toma del JWT), que no tenia una ejecucion Java local posterior al cambio.

Las pruebas cubren JWT ausente, invalido, de otro emisor o firmado con otra clave; roles de administrador, organizador y personal de acceso; bloqueo de rutas con segmento `interno`; JWT de servicio con `ROLE_SERVICIO_INTERNO`; reemplazo de `X-Organizador-Id` con el `sub` verificado; e identidad de cliente/administrador tomada del token en compras, reservas, filas y solicitudes. La matriz de autorizacion del Gateway sigue siendo la fuente autoritativa de rutas y roles; no se debilitaron controles para hacer pasar pruebas.

Se adapto `msv-usuario/scripts/Test-ComprasEnComun.ps1` al flujo autenticado: login por usuario, JWT por persona, alto de rol del administrador de prueba mediante `/auth/registro` y `PUT /usuarios/{id}`, JWT de servicio con `SERVICIO_INTERNO` (y `usuarioId` cuando aplica) para rutas `/interno/**` y reservas de Sectores, y expectativas `401/403` en las sondas internas del Gateway. Se conservaron los escenarios originales, las comprobaciones de reintegros y caidas, y la limpieza de fixtures.

En `tests/system/Test-System.ps1` se anadieron comprobaciones de identidad: el evento debe conservar el `idOrganizador` del JWT aunque el cuerpo y `X-Organizador-Id` intenten suplantar otro usuario; la compra debe devolver el `idCliente` del JWT; la auditoria mantiene cinco sondas `401/403` y verifica que un `X-Organizador-Id` falsificado no devuelva eventos ajenos en `/api/eventos/mis-eventos`.

La sintaxis de `Test-ComprasEnComun.ps1`, `Test-System.ps1` y `Common.ps1` se comprobo con el parser de Windows PowerShell 5.1 usando copias temporales con BOM. Se valido `docker compose config --quiet` para `docker-compose.yml` y `tests/recomendaciones/compose.hu10.yml` con variables de ejemplo.

### Ejecucion del sistema con Docker (21/11/2026)

Se levanto el stack completo con `docker compose` (ocho servicios saludables: `gateway`, `usuarios`, `eventos`, `sectores`, `compras`, `mongodb`, `mysql` y `redis`) y se ejecuto de extremo a extremo `tests/system/Test-System.ps1 -AuditSecurity` con PowerShell 7. El informe `.local/system-tests/integration-security.json` registro `passed=true`, `securityPassed=true` y `overallPassed=true` (11:39:27 a 11:39:29, hora local). Pasaron las siete comprobaciones de integracion: creacion de datos con identidad verificada, borrador privado e inventario obligatorio al publicar, consulta de Eventos desde Sectores, compra idempotente con pago simulado y QR, rechazo de solicitudes ajenas, reemplazo de `X-Organizador-Id` por el `sub` del JWT, y devolucion con reintegro idempotente y restitucion del cupo. Las cinco sondas de seguridad devolvieron 401 o 403: lectura anonima de usuarios, lectura anonima de compra ajena, token invalido, ruta `interno` expuesta por Gateway y suplantacion por `X-Organizador-Id`. La integracion creo datos nuevos y los conservo para inspeccion; no se borraron los existentes.

### Medicion HU10 autenticada (21/11/2026)

Se ejecuto `tests/recomendaciones/Test-HU10-Rendimiento.ps1` con el flujo autenticado de Stage 3: 10.000 eventos sembrados en bases temporales aisladas, 10 consultas de calentamiento y 100 consultas medidas por Gateway con JWT de CLIENTE. p50 290,39 ms, p95 463,13 ms, maximo 640,18 ms; objetivo p95 <= 2.000 ms cumplido. `passed=true`, `baselineCoincidente=true` y `cleanupPassed=true`. El informe esta en `.local/system-tests/hu10-performance-result.json`; el script no calcula p99.

### Recorrido real de compras en comun (21/11/2026)

Se ejecutaron los cuatro modos de `msv-usuario/scripts/Test-ComprasEnComun.ps1` contra el stack Docker con PowerShell 7: por defecto, `-TestOutage`, `-TestUnificadas` y `-TestInventario`. Los cuatro terminaron con `passed=true` y `cleanupPassed=true` y `cleanupErrors=[]`; los tres ultimos marcaron `outageVerified`, `unifiedVerified`/`historyVerified` e `inventoryVerified` respectivamente. Se verificaron el conteo distinto de compradores MySQL, la privacidad del historial, la deduplicacion, reintegros parciales y totales, la union de fuentes, la exclusion comun, el agotamiento y restitucion de cupos, y las caidas y recuperacion de Compras y Eventos con 503 sin perdida de historial. Las llamadas internas usaron JWT de servicio `SERVICIO_INTERNO` (con `usuarioId` en reservas); las rutas `interno` bloqueadas en Gateway devolvieron 401/403. No se modificaron reglas de autorizacion ni codigo de produccion; el unico cambio fue corregir dos llamadas del propio script que pasaban argumentos de mas y descartaban el token de administrador.

Posteriormente se ejecuto el escenario combinado `-TestInventario -TestOutage`, que ademas interrumpe Sectores: termino con `passed=true`, `cleanupPassed=true`, `cleanupErrors=[]`, 10 comprobaciones y todas las banderas en true, incluidas `inventoryVerified` e `inventoryOutageVerified`. Durante la caida se confirmo la respuesta parcial por JSON y por las cabeceras `X-Disponibilidad-Verificada: false` y `X-Respuesta-Parcial: true` a traves de Gateway; al restaurar Sectores se recupero la verificacion completa de inventario. El inventario final (inventarios, reservados y vendidos por sector) quedo identico al estado inicial y los ocho servicios volvieron a estar saludables. El informe queda en `msv-usuario/.local/compras-en-comun-result.json` (ultimo modo ejecutado).

### Prueba de carga autenticada (21/11/2026)

Se ejecuto `pwsh -NoProfile -File ./tests/system/Test-Load.ps1 -Concurrency 10 -Requests 500` contra el Gateway (`http://127.0.0.1:8080`) con un JWT de CLIENTE obtenido del fixture autenticado `.local/system-tests/integration-security.json`. El trabajo fue de 500 solicitudes con 10 trabajadores paralelos sobre tres rutas protegidas y enrutadas por Gateway: `/api/eventos` (167/0 errores/p95 111,6116 ms), `/compras/cliente/20` (166/0/p95 134,2892 ms) y `/eventos/7/sectores/8/disponibilidad` (167/0/p95 324,2423 ms). Resultado: 0 errores, p50 55,2836 ms, p95 168,0607 ms, maximo 523,4679 ms, 53,2923979502806 solicitudes/s y 9,3822 s de duracion. Umbrales intactos (p95 <= 2.000 ms, errores <= 1%): `passed=true` global y por endpoint (`EXITCODE=0`). El script es de solo lectura, no crea fixtures y no requiere limpieza; el informe quedo en `.local/system-tests/load.json`.

### Concurrencia de compras en el stack actual (21/11/2026)

Se ejecuto un arnes temporal autenticado por Gateway contra la instancia unica de Compras: capacidad 10, 30 compras simultaneas con claves idempotentes distintas, reenvio de esas 10 claves, 10 pagos simultaneos y reintento de pago. Resultado `201:10, 409:20`, 0 respuestas 5xx y 10 IDs unicos; tras las reservas `disponibles=0, reservados=10, vendidos=0`; el reenvio devolvio los mismos 10 IDs sin alterar el inventario; tras los pagos `vendidos=10, reservados=0` y 10 entradas emitidas. `passed=true`, `cleanupPassed=true`, `cleanupErrors=[]`, 6 comprobaciones. Informe `.local/system-tests/concurrent-single.json`. El arnes quedo fuera del repositorio (`C:\Users\Nur\AppData\Local\Temp\opencode\concurrent-single.ps1`) porque opera sobre los datos del stack principal; su limpieza elimino solo sus propias filas y usuarios. Durante la diagnosis aparecio un hallazgo: `POST /compras/eventos/{id}/cancelacion` devolvio 503 de forma sostenida, mientras Eventos estaba saludable y `PATCH /api/eventos/{id}/cancelar` por Gateway devolvia 200. El fallo queda aislado en la llamada Feign interna de cancelacion de Compras; no se cambio codigo de produccion y la limpieza uso las rutas que funcionan mas borrado selectivo de las filas propias.

### Concurrencia de compras con varias instancias (21/11/2026)

Se agregaron `tests/system/compose.multinstance.yml` y `tests/system/Test-Concurrencia-MultiInstancia.ps1`: entorno aislado con sufijo GUID de 2 Gateway, 2 instancias de Compras, 2 de Sectores, 1 de Eventos y 1 de Usuarios, cuatro bases MySQL y una MongoDB temporales, cuenta MySQL y secreto JWT propios. Usa las imagenes locales del sistema, no define volumenes y no toca el catalogo ni los datos principales. Las 30 compras se repartieron entre ambos Gateway con JWT de CLIENTE: 15 por JVM, confirmado en los logs (`compras-a=15`, `compras-b=15`). Resultado `201:10, 409:20`, 0 5xx, 10 IDs unicos y sin overselling (`disponibles=0, reservados=10, vendidos=0`). El reenvio cruzado por el Gateway opuesto devolvio los mismos IDs sin duplicar compras ni cupos; 10 pagos paralelos en dos JVM y su reintento dejaron `vendidos=10, reservados=0` y 10 entradas. `passed=true`, `cleanupPassed=true`, `cleanupErrors=[]`, 8 comprobaciones. Informe `.local/system-tests/concurrent-multi.json`.

Los 10 cupos los ganaron solicitudes atendidas por `compras-a`; las 15 de `compras-b` fueron rechazadas con 409 por el inventario compartido, lo que confirma la contencion real entre JVM. Las ganadoras no se reparten por JVM porque las decide el bloqueo pesimista de la base, no el reparto del trafico. No se modificaron reglas de seguridad, rutas del Gateway ni codigo de produccion; la comprobacion de duplicados se hizo explicitamente cruzando de Gateway para demostrar que la idempotencia esta en la base y no en la memoria de una instancia.

### Colecciones Postman autenticadas por Gateway (21/11/2026)

Las colecciones originales apuntaban a puertos directos y no enviaban JWT. Se ejecutaron copias temporales en `C:\Users\Nur\AppData\Local\Temp\opencode\postman-phase3\` con `auth=bearer`, `baseUrl`/`eventosBaseUrl=http://127.0.0.1:8080` y entornos con JWT de ADMINISTRADOR y CLIENTE. `newman` 6.2.3. Comando general: `npx.cmd --yes newman run "$d\<coleccion>.json" --environment "$d\admin.postman_environment.json" --reporters cli --timeout-request 30000`.

| Coleccion | Solicitudes | Scripts de prueba | Aserciones | Duracion | Resultado |
| --- | ---: | ---: | ---: | ---: | --- |
| Eventos | 46/46 | 46 | 87 | 4,6 s | 0 fallos, `EXITCODE=0` |
| Usuarios | 70/70 | 140 | 254 | 9,1 s | 0 fallos, `EXITCODE=0` |

Logs: `C:\Users\Nur\AppData\Local\Temp\opencode\postman-eventos-4.log` y `postman-usuarios.log`. El 21/11 se repitio Eventos como regresion final tras alinear la coleccion del repositorio con `auth=bearer` (`{{jwtToken}}`) y `baseUrl=http://127.0.0.1:8080`: 46/46, 87 aserciones, 0 fallos, 5,3 s (`postman-eventos-final-regression.log`).

Se omitieron por seguridad de datos: las colecciones de Compras y Sectores (IDs fijos de produccion y mutan inventario/datos existentes) y cuatro solicitudes de Usuarios que modifican roles compartidos (`10 Repetir el mismo tipo del rol`, `22 Crear rol duplicado`, `23 Rol sin tipo`, `25 No permite eliminar el rol utilizado`). No se cuentan como aprobadas.

El recorrido de Eventos estaba desactualizado y se corrigio solo el arnes: se agregaron creacion de sector e inventario antes de publicar, cancelacion de inventario antes de borrar el evento, expectativa de cancelacion idempotente `200` (el servicio devuelve el evento ya cancelado) y expectativa `409` al eliminar un sector protegido por configuracion de evento. La coleccion del repositorio tambien paso de `noauth`/puerto 8005 a bearer `{{jwtToken}}`/Gateway 8080 para que la documentacion y la ejecucion sean consistentes; el token se inyecta desde el entorno temporal. No se cambio codigo de produccion ni se debilitaron controles.

### Scripts funcionales de Usuarios por Gateway (21/11/2026)

Los cuatro scripts originales usaban puertos directos y sin JWT. Se ejecutaron copias temporales en `C:\Users\Nur\AppData\Local\Temp\opencode\phase4\`, generadas por `prepare-phase4.ps1`, con las mismas URL base del Gateway y `Authorization: Bearer <JWT>` inyectado. No se ejecutaron los modificadores `-TestOutage`, `-RestartService`, `-TestEventosUnavailable` ni `-RunPostman` para no detener servicios.

| Script | Comando | Resultado |
| --- | --- | --- |
| `Test-Recomendaciones.ps1` | `pwsh -NoProfile -File '...\phase4\Test-Recomendaciones.ps1'` | `EXITCODE=0`, 8 comprobaciones, `passed=true` |
| `Test-Preferencias.ps1` | `pwsh -NoProfile -File '...\phase4\Test-Preferencias.ps1'` | `EXITCODE=0`, 6 comprobaciones, `passed=true` |
| `Test-Api.ps1` | `pwsh -NoProfile -File '...\run-phase4-api.ps1'` | `EXITCODE=0`, CRUD/favoritos/409 y eliminacion |
| `Test-Artistas-Reales.ps1` | `pwsh -NoProfile -File '...\phase4\Test-Artistas-Reales.ps1'` | `EXITCODE=0`, 4 comprobaciones, `passed=true` |

El adaptador `run-phase4-api.ps1` crea un artista temporal por Gateway, invoca la copia adaptada de `Test-Api.ps1` con ese ID real y lo elimina al finalizar. Se excluyo de la copia la unica comprobacion que intentaba `DELETE /roles/{id}` sobre el rol CLIENTE compartido; se reporta como omitida, igual que en Postman. Informes: `phase4-recomendaciones.json`, `phase4-preferencias.json` y `phase4-artistas.json` en Temp. Verificacion posterior: 0 fixtures residuales de Fase 4, 6 eventos, 8 artistas, 26 usuarios y ocho servicios saludables.

### Cobertura de Eventos, esquema y Maven (21/11/2026)

La coleccion de Eventos de 46 solicitudes cubre los cinco temas pendientes: cuerpos y enumeradores invalidos (solicitudes 20 a 25, 34, 35, 37, 38, 41), paginacion (18), edición (3, 7, 11), busqueda (13, 16 a 19, 28) y cambios de estado (14 a 17, 26 a 31, 34). No se agregaron pruebas nuevas porque la cobertura ya estaba demostrada y ejecutada por Gateway con JWT.

La revision de esquema confirmo que no hay Flyway/Liquibase: los servicios usan `DDL_AUTO=update` en desarrollo y la documentacion exige respaldo/migracion revisada para datos previos. Tampoco hay POM agregador raiz; las cinco suites se ejecutan con `.\mvnw.cmd test` por modulo. No se introdujo migracion ni automatizacion nueva: cualquiera de esos cambios seria arquitectonico y requiere decision previa.

### Carga de consultas con varias instancias (21/11/2026)

Se ejecuto dos veces el arnes temporal `C:\Users\Nur\AppData\Local\Temp\opencode\multinstance-query-load.ps1`, apoyado en `tests/system/compose.multinstance.yml`: entorno aislado con sufijo GUID, 2 Gateway, 2 Compras, 2 Sectores, 1 Eventos y 1 Usuarios, bases/cuenta/JWT temporales, sin volumenes. Trabajo: 600 solicitudes, concurrencia 10, cuatro rutas protegidas, 300 por Gateway, 0 errores y limpieza correcta en ambas corridas.

- Corrida 1: p50 233,3526 ms, p95 1195,2887 ms, maximo 1990,2682 ms, `passed=true`. Solo queda en `C:\Users\Nur\AppData\Local\Temp\opencode\multinstance-query-load.log`.
- Corrida 2: p50 441,4234 ms, p95 2079,4963 ms, maximo 3944,7407 ms; `/usuarios/{id}/recomendaciones?limite=10` p95 2626,3706 ms supero el umbral de 2.000 ms. `passed=false`, `cleanupPassed=true`, `cleanupErrors=[]`. Informe `.local/system-tests/multinstance-load.json` y log `multinstance-query-load-2.log`.

Una sonda independiente (`check-gateway-routing.ps1`) confirmo que cada Gateway alcanza una JVM distinta de Compras. Los contadores por JVM durante la carga quedaron asimetricos (`comprasA=1`, `comprasB=151`) y no se usan como prueba del reparto; el registro real de destino fue 300/300 por Gateway. El resultado de esta carga es mixto y no se presenta como aprobado.

### Estado de pendientes tras esta verificacion
- Completado: colecciones Postman Eventos y Usuarios autenticadas; cuatro scripts funcionales de Usuarios por Gateway; cobertura de Eventos revisada; revision de esquema y Maven sin cambios de infraestructura.
- Abierto: estabilizar la carga de consultas multi-instancia; la repeticion supero p95 en recomendaciones (2626,3706 ms).
- Abierto: versionado de esquema con migraciones y ejecucion conjunta de las suites Maven; no se introdujo infraestructura nueva sin aprobacion.
- Abierto: diagnosticar `POST /compras/eventos/{id}/cancelacion` (503 sostenido).
- No ejecutadas: colecciones Postman de Compras y Sectores, y cuatro solicitudes de roles compartidos en Usuarios.

Las secciones "Verificacion anterior" describen el estado de cada etapa en su momento; la situacion vigente es la del 21/11 indicada al inicio.

## Verificacion anterior: seguridad del Gateway (Stage 2)

Se completo la validacion de JWT en el Gateway con el mismo HS256, secreto e issuer que Usuarios. La suite completa del modulo quedo en **21 pruebas, 0 fallos, 0 errores y 0 omitidas**: 1 de arranque, 6 de rutas de Usuarios, 2 de inventario, 4 de compras y 8 de seguridad. Las pruebas usan proveedores HTTP simulados y JWT generados con el secreto del perfil `test`, por lo que no requieren Docker. En la etapa anterior, la suite de Usuarios habia quedado en 179 pruebas correctas con una base MongoDB temporal; no se repitio en esta verificacion.

Las pruebas cubren `/auth/**`, tokens ausentes, invalidos, vencidos, de otro emisor o firmados con otro secreto; roles de administrador, organizador y personal de acceso; propiedad de recursos por el `sub` del JWT; bloqueo de cualquier ruta con el segmento `interno`; y reemplazo de `X-Organizador-Id` con la identidad verificada. El resto de `/actuator/**` tambien queda denegado por configuracion; `/actuator/health` sigue publico.

Se valido `docker compose config --quiet` para `docker-compose.yml` y `tests/recomendaciones/compose.hu10.yml` con variables de ejemplo. La sintaxis de los scripts `Common.ps1`, `Test-System.ps1`, `Test-Load.ps1` y `Test-HU10-Rendimiento.ps1` se comprobo estaticamente. `pwsh` no esta disponible en este entorno: el ultimo script se valido con el parser de Windows PowerShell 5.1 usando una copia temporal con BOM, por el caracter no ASCII de una ciudad de prueba. Los scripts requieren PowerShell 7 para ejecutarse.

Los scripts de sistema y HU10 se actualizaron para autenticarse por Gateway, pero **no se ejecutaron de extremo a extremo** contra servicios reales en esta verificacion. No se repitieron la medicion de rendimiento ni las suites Java de los demas microservicios. La auditoria automatica ahora espera 401 o 403 en los cinco controles; no se afirma su resultado real sin una ejecucion con Docker. Los microservicios aguas abajo incorporaron la validacion local en la etapa siguiente.

## Verificacion anterior: consultas filtradas y por lote (HU10)

168 pruebas Java de Usuarios, 11 de Eventos y 12 de Gateway: 191 sin fallos ni omitidas. Cubren coincidencias OR, normalizacion de ciudad, lote de candidatos/referencias, ausencia de limite anticipado, carga SQL sin N+1, varios artistas sin participantes duplicados, reutilizacion de IDs, consultas independientes y sus fallos, validacion y rutas internas fuera de Gateway. No se repitieron las suites Java de Compras ni Sectores en esta etapa.

Postman: 148 solicitudes y 538 aserciones sin fallos. El recorrido real con Compras, MongoDB, MySQL e inventario conservo puntuaciones, motivos, exclusiones y orden; paso reintegros y caidas/recuperacion. Reservas liberadas, entradas reintegradas, inventario restituido y perfiles temporales eliminados. Eventos y Usuarios fueron reconstruidos y desplegados.

Objetivo acordado: 100 consultas secuenciales con 10.000 eventos y p95 <= 2.000 ms. La version final midio p50 271,42 ms, p95 339,73 ms y maximo 399,78 ms tras diez consultas de calentamiento, por Gateway y con proveedores reales. Escenario selectivo: 60 candidatos por preferencias, tres retenidos sin cupos y diez resultados por consulta; todos coinciden con el baseline calculado desde el catalogo completo del mismo conjunto. Historial de Compras vacio en esta medicion; los casos positivos de compras se probaron en el recorrido funcional separado. No mide carga concurrente ni catalogos donde todos los eventos coinciden.

Las bases, cuenta MySQL y cuatro contenedores temporales del benchmark fueron retirados. Informes: `.local/system-tests/hu10-performance-result.json`, `hu10-real-result.json`, logs `hu10-performance.log`, `hu10-real.log`, `hu10-postman.log`, `hu10-eventos-java.log`, `hu10-gateway-java.log` y `msv-usuario/.local/postman/hu10-java.log`. [Reproducir medicion](../recomendaciones/README.md).

## Verificacion anterior: disponibilidad para recomendaciones (HU9)

164 pruebas Java de Usuarios, 43 de Sectores/Asientos y 12 de Gateway: 219 sin fallos ni omitidas. Incluyen reservas vigentes y vencidas, cupos de campo y asientos numerados, varios sectores, inventario inexistente/cancelado, lote unico solo para candidatos y filtro antes del limite. Verifican respuestas parciales ante inventario caido o contrato incompleto, cabeceras y la consulta unica de catalogo de HU7. No se repitio la suite Java de Compras en esta etapa.

Postman: 148 solicitudes, 538 aserciones y cero fallos. Prueba real MongoDB/MySQL por Gateway: se retuvieron temporalmente todos los cupos del candidato con reservas propias; desaparecio de ambas fuentes mientras el catalogo seguia EN_CURSO y reaparecio al liberar una entrada. La caida de Sectores produjo `disponibilidadVerificada=false` y respuesta parcial, incluso por cabeceras a traves de Gateway. Recuperacion correcta, incluidas las regresiones de HU6/HU8 y las caidas de Compras y Eventos.

Usuarios y Sectores fueron reconstruidos y desplegados. Limpieza correcta: reservas temporales liberadas, entradas reintegradas, inventario restituido y perfiles temporales eliminados. Informe estable `.local/system-tests/hu9-real-result.json`; logs `hu9-real.log`, `hu9-postman.log`, `hu9-sectores-java.log`, `hu9-gateway-java.log` y `msv-usuario/.local/postman/hu9-java.log`. [Contrato actualizado](../../msv-usuario/REST.md).

## Verificacion anterior: exclusion por historial valido (HU8)

157 pruebas Java de Usuarios, 85 de Compras y 12 de Gateway: 254 sin fallos ni omitidas. Verifican historial propio distinto, estados validos, reintegros parciales/totales, exclusion comun de ambas fuentes antes del limite, historial no verificado y transmision de cabeceras por Gateway. Se conserva la prueba de una sola consulta de catalogo de HU7.

Postman: 148 solicitudes y 538 aserciones sin fallos; valida el nuevo campo `historialVerificado` y las cabeceras de las fuentes individuales. Prueba real por Gateway con MongoDB/MySQL/Eventos: el evento desaparece al comprar y solo reaparece al reintegrar todas las entradas de todas las compras. Se probaron caidas y recuperacion de Compras y Eventos, con respuesta parcial e historial no verificado cuando corresponde. Servicios restaurados, inventario restituido, entradas temporales reintegradas y perfiles temporales eliminados.

Usuarios y Compras fueron reconstruidos y desplegados. Reportes: `.local/system-tests/hu8-real-result.json`, `hu8-real.log`, `hu8-postman.log`, `hu8-compras-java.log`, `hu8-gateway-java.log` y `msv-usuario/.local/postman/hu8-java.log`. [Contrato actualizado](../../msv-usuario/REST.md).

## Verificacion anterior: catalogo compartido (HU7)

150 pruebas Java de Usuarios, sin fallos ni omitidas. Las nuevas pruebas HTTP usan ambas fuentes reales y verifican exactamente una invocacion a Eventos por solicitud unificada, tanto con catalogo valido como ante un fallo o respuesta nula; una solicitud posterior vuelve a consultar. Los endpoints individuales conservan sus calculos y Compras sin candidatos no consulta Eventos.

Postman: 148 solicitudes, 532 aserciones y cero fallos. Prueba real de recomendaciones con compras simuladas por Gateway: puntuaciones, motivos, deduplicacion, orden y limites correctos; inventario restituido, entradas reintegradas y perfiles temporales eliminados. No se detuvieron dependencias ni se repitieron suites Java de otros modulos en esta etapa. Usuarios fue reconstruido y desplegado en Docker.

Reportes: `msv-usuario/.local/postman/hu7-java.log`, `.local/system-tests/hu7-postman.log`, `hu7-real.log` y `hu7-real-result.json`. El conteo de llamadas se verifica mediante los mocks Feign de las pruebas Java; Postman comprueba el contrato HTTP conservado.

## Verificacion anterior: recomendaciones unificadas (HU6)

142 pruebas Java de Usuarios y 11 de Gateway, sin fallos ni omitidas. Postman: 148 solicitudes, 532 aserciones y cero fallos. Prueba real por Gateway con MongoDB, compras MySQL y Eventos: union sin duplicados, motivos, puntuacion, limites, fuentes por separado y respuestas vacias; 200 parcial con Compras detenido, 503 cuando ambas fuentes requieren Eventos y este no responde, y 200 parcial vacio cuando Compras responde sin candidatos. Los servicios se restauraron; inventario restituido, entradas temporales reintegradas y perfiles temporales eliminados. No se repitieron las suites Java de otros modulos en esta verificacion.

Reportes: `msv-usuario/.local/compras-en-comun-result.json`, `msv-usuario/.local/postman-real/results.json`, `msv-usuario/.local/postman/hu6-java.log` y `.local/system-tests/hu6-*.log`. [Contrato y cambio de respuesta](../../msv-usuario/REST.md): el endpoint principal ahora devuelve un objeto con `recomendaciones`, `parcial` y `fuentesNoDisponibles`.

## Verificacion anterior: migracion a MongoDB

Usuarios usa MongoDB y se comunica con el catalogo real de artistas de Eventos. Los siete contenedores quedaron saludables. Se ejecutaron las suites Java de los cinco modulos: 138 pruebas correctas. Postman contra artistas reales: 64 solicitudes, 128 aserciones, 0 fallos. La caida/recuperacion de Eventos y el artista eliminado se comprobaron por HTTP.

El recorrido funcional completo por Gateway paso: compra, idempotencia, pago simulado, QR, devolucion y restitucion del cupo. Carga: 500 consultas con 10 trabajadores, 0 errores, p95 77,42 ms. La auditoria de seguridad mantuvo los cinco hallazgos previos; devolvio codigo 2, con passed=true y securityPassed=false. No se considera una aprobacion global de seguridad.

Ver [detalle de cambios, resultados y limites](../../msv-usuario/docs/RESULTADOS-MONGODB.md). Informes locales actuales en `.local/system-tests/` y `msv-usuario/.local/postman-real/`. La prueba de sistema conserva fixtures para inspeccion; los datos temporales del script de artistas fueron eliminados.

---

## Historico — 14/11/2026

Configuracion de aquella verificacion: cinco aplicaciones y un MySQL con cuatro esquemas. Los seis contenedores quedaron saludables.

| Verificación | Resultado |
| --- | --- |
| Migración de Compras | Cuatro tablas copiadas, con filas y checksums coincidentes. |
| Permisos MySQL | Cuatro cuentas disponibles y doce accesos cruzados rechazados. |
| Integración real por Gateway | Compra, pago simulado, QR, devolución y restitución de inventario correctos. |
| Controles puntuales | Borrador oculto (404), organizador ajeno (403), encabezado ausente (400), devolución de otro cliente (403) y compra inválida (400). |
| Carga de lectura | 500 solicitudes, 10 trabajadores, 0 errores; p95 141,44 ms y 48,34 solicitudes/s. Pasó los umbrales. |

La auditoría detectó cinco vulnerabilidades: acceso anónimo a usuarios y compras, aceptación de token inválido, API interna expuesta por Gateway y suplantación mediante X-Organizador-Id. Las cinco consultas recibieron 200. La autenticación sigue fuera del alcance de esta etapa; estas vulnerabilidades no se corrigieron.

Los respaldos de la unificación están en .local/backups/unificar-compras-20261114-154656-faf91eb0/. El contenedor anterior de Compras quedó detenido y su volumen se conserva. El migrador rechazó correctamente un intento de sobrescribir el destino ya migrado.

Los informes y muestras están en .local/system-tests/, excluidos de Git. Las pruebas agregan datos nuevos; las compras de integración se devuelven. La carga mide consultas locales, sin extrapolar capacidad de producción.

También se validaron la sintaxis de los scripts, Compose y el diff. No se repitieron las suites Maven en esta verificación; construir las imágenes no las ejecuta.

[Repetir las pruebas](README.md) · [Configuración MySQL](../../docker/mysql/README.md)
