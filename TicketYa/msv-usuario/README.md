# Microservicio de usuarios con MongoDB

Java 21 y Spring Boot. Colecciones: `usuarios`, `roles` y `secuencias` (contador de IDs numericos). Los favoritos se guardan como IDs dentro del usuario. Los indices unicos protegen email, DNI y nombre del rol; `@Version` evita sobrescrituras de perfiles desactualizados.

## Sistema real: Usuarios y artistas de Eventos

Desde la raiz, con Docker Desktop iniciado:

```powershell
# Si antes se uso el entorno aislado, detenerlo para liberar 8004 y 27017.
docker compose -f msv-usuario/compose.yml stop
# Solo lo necesario para Usuarios y artistas reales.
docker compose up --build -d --wait --wait-timeout 600 mongodb mysql eventos usuarios
# Opcional: sistema completo con Gateway, Sectores y Compras.
docker compose up --build -d --wait --wait-timeout 600
```

Usuarios usa `MONGODB_URI=mongodb://mongodb:27017/usuarios` y consulta `MSV_EVENTO_URL=http://eventos:8005`. Eventos guarda artistas en su esquema MySQL y expone `GET /api/artistas/{id}`. Usuarios acepta el campo `id` del catalogo y publica `idArtista` en su propia respuesta.

Agregar un favorito almacena el ID; listar consulta nombre y genero actuales en Eventos. Si el artista fue eliminado, responde 502; si el catalogo no esta disponible, responde 503 y conserva los favoritos. No hay transaccion distribuida entre servicios.

| Recurso | Direccion |
| --- | --- |
| Usuarios directo | http://localhost:8004 |
| Artistas reales | http://localhost:8005/api/artistas |
| Gateway, si se inicio | http://localhost:8080 |
| Roles inicializados | http://localhost:8004/roles |
| OpenAPI | http://localhost:8004/openapi.json |
| MongoDB / DataGrip / Compass | mongodb://localhost:27017/usuarios |

MongoDB local no requiere usuario ni clave (No auth). Esta publicado solo en localhost. El perfil dev inicializa cuatro roles y un administrador de prueba sin credenciales. La etapa actual funciona sin token.

## Pruebas con artistas reales

Desde la raiz, con el Compose principal iniciado:

```powershell
# Crea su propio artista, prueba la API y la coleccion completa; limpia sus datos.
.\msv-usuario\scripts\Test-Artistas-Reales.ps1 -RunPostman
# Tambien detiene y restaura Eventos para probar 503 y recuperacion.
.\msv-usuario\scripts\Test-Artistas-Reales.ps1 -RunPostman -TestOutage
```

El script comprueba CRUD, duplicados, favoritos, actualizacion de nombre/genero en Eventos, artista eliminado (502) y, opcionalmente, caida real (503) y recuperacion sin perder favoritos. `-TestOutage` interrumpe brevemente Eventos y solo debe usarse en el entorno local de pruebas.

La coleccion [TicketYa-Usuarios.postman_collection.json](TicketYa-Usuarios.postman_collection.json) tiene 74 solicitudes. Reimportarla en Postman y ejecutar completa de 00 a 33 con `baseUrl=http://localhost:8004`, `eventosBaseUrl=http://localhost:8005` y `modoArtistas=real`. La solicitud 00 crea un artista temporal y captura su ID; la 33 lo elimina. No hace falta elegir un artista manualmente. Si se interrumpe, revisar los IDs capturados antes de limpiar sus datos. Evitar variables de entorno viejas que sobrescriban las URLs o el modo.

## Entorno aislado con simulador

Desde la raiz, detener primero el entorno principal para liberar los puertos y evitar dos MongoDB sobre el mismo volumen:

```powershell
docker compose stop
docker compose -f msv-usuario/compose.yml up --build -d --wait --wait-timeout 600
.\msv-usuario\scripts\Test-Api.ps1
```

Este Compose construye `ticketya/usuarios:local` e inicia Usuarios, MongoDB y un simulador de artistas. El simulador contiene 900001 (Soda Stereo) y 900002 (Mercedes Sosa); no es Eventos real. Para este modo configurar `modoArtistas=simulador`: omite crear/eliminar artistas y usa 900001 automaticamente.

## Java y pruebas desde IntelliJ

Detener el contenedor de Usuarios, conservar MongoDB y Eventos:

```powershell
docker compose stop usuarios
docker compose up -d --wait mongodb mysql eventos
```

Ejecutar `MsvUsuarioApplication` desde IntelliJ. Usa MongoDB en localhost:27017 y Eventos en localhost:8005 por defecto. El resto del sistema puede seguir funcionando por Gateway hacia el puerto 8004.

Desde `msv-usuario`, con JDK 21 o superior:

```powershell
.\mvnw.cmd test
```

Las pruebas de integracion usan MongoDB real en localhost:27017. `TEST_MONGODB_URI` permite elegir otro servidor. Cada ejecucion asigna una base `usuarios_test_<UUID>` y la elimina al terminar cada clase; nunca borra `usuarios`. Prueban persistencia, HTTP con MockMvc, indices unicos, IDs concurrentes, favoritos concurrentes, bloqueo optimista y el contrato JSON de artistas. Las pruebas Java del servicio usan un cliente de artistas simulado; el script HTTP anterior verifica la comunicacion real.

El Dockerfile usa `-DskipTests`: compila las pruebas pero no las ejecuta durante el empaquetado. La suite Java se ejecuta por separado con MongoDB disponible.

## Datos y comandos

El Compose principal y el aislado usan el volumen `ticketya-usuarios-local_usuarios_mongo`; nunca deben ejecutar MongoDB simultaneamente. Detener contenedores conserva datos. `down` conserva volumenes; `down -v` elimina datos. Los usuarios antiguos de MySQL no se importan automaticamente ni se borran.

Desde la raiz, con el Compose principal:

```powershell
docker compose ps
docker compose logs --tail 80 usuarios
docker compose exec mongodb mongosh usuarios --eval 'db.usuarios.find().toArray()'
docker compose stop
```

Los endpoints y ejemplos estan en [REST.md](REST.md).


## Verificacion anterior de la coleccion (antes de preferencias)

16/11/2026: dos recorridos completos contra Usuarios y Eventos reales, 68 solicitudes y 258 aserciones, sin fallos de solicitudes, scripts ni aserciones. Se validan campos completos y tipos, perfiles devueltos frente a lo enviado y guardado, roles, ID/nombre/genero exactos de los favoritos, ausencia de duplicados, estados de error y limpieza de los fixtures. El ejecutor usa el archivo opcional de datos si existe; si no, genera dos recorridos con valores unicos. Reporte local: `.local/postman-real/results.json`.


## Preferencias de eventos

`PUT /usuarios/{id}/preferencias` guarda `tiposEventoPreferidos` y `ciudadPreferida` en el mismo documento de Usuario, sin cambiar el perfil ni los artistas favoritos. Tipos permitidos, cuerpos, vaciado y errores se documentan en [REST.md](REST.md). Las respuestas de usuario incluyen ambos campos nuevos.

La coleccion Postman incorpora guardar, deduplicar, modificar y vaciar preferencias, errores 400/404 y conservacion de datos. Para comprobar persistencia tras un reinicio real, desde la raiz: `.\msv-usuario\scripts\Test-Preferencias.ps1 -RestartService`. Crea y limpia sus propios usuario y artista; requiere Gateway y Eventos activos.

Verificacion de esta funcionalidad: 42 pruebas Java de Usuarios y 90 solicitudes Postman con 342 aserciones, sin fallos. Reportes locales en `.local/postman/preferencias-java.log` y `.local/postman-real/results.json` dentro del modulo.

Se verifico tambien un reinicio real del contenedor Usuarios: las preferencias, datos del perfil y artistas favoritos se conservaron. Resultado en `.local/preferencias-result.json`.


## Consultar preferencias (HU2)

`GET /usuarios/{id}/preferencias` devuelve `tiposEventoPreferidos`, `ciudadPreferida` (null si no esta definida) y `artistasFavoritos` como IDs. Lee MongoDB sin consultar Eventos ni modificar al usuario. Usuario inexistente: 404. La coleccion Postman incluye consultas de datos vacios, guardados y modificados.

Prueba real con Eventos caido, desde la raiz: `.\msv-usuario\scripts\Test-Preferencias.ps1 -TestEventosUnavailable`. Detiene y restaura Eventos; usar solo en desarrollo. La suite Java compara el documento completo antes/despues de consultar y verifica que no se llame al cliente de artistas.


Verificacion HU2 (16/11/2026): 48 pruebas Java de Usuarios, 102 solicitudes Postman y 388 aserciones sin fallos. Se comprobo por Gateway que GET preferencias devuelve 200 con Eventos detenido mientras GET artistas-favoritos devuelve 503. Eventos fue restaurado y los datos de prueba fueron eliminados. La consulta no modifica el documento ni su version, verificado comparando el BSON completo antes y despues. Reportes: `.local/postman/hu2-java.log`, `.local/postman-real/results.json` y `.local/preferencias-result.json`.


## Artistas favoritos (HU3)

Se reutilizan PUT/DELETE de `/usuarios/{id}/artistas-favoritos/{idArtista}` y GET de `/usuarios/{id}/artistas-favoritos`. Las altas y bajas son idempotentes, rechazan IDs no positivos y conservan las otras preferencias. Los detalles vienen de Eventos; los errores no borran datos.

Desde la raiz, con el sistema iniciado: `.\msv-usuario\scripts\Test-Artistas-Reales.ps1 -RunPostman -TestOutage`. Ejecuta la coleccion ampliada y verifica preferencias con artista real, cambios de nombre/genero, caida/recuperacion de Eventos y artista eliminado. Interrumpe brevemente Eventos y lo restaura; usar solo en desarrollo. La suite Java agrega comparaciones de documentos completos en MongoDB.


Verificacion HU3 (16/11/2026): 61 pruebas Java de Usuarios, 126 solicitudes Postman y 462 aserciones, sin fallos. El recorrido real comprobo altas/bajas repetidas, conservacion de perfil/tipos/ciudad preferida, nombre y genero desde Eventos, caida con 503 sin perder datos, recuperacion y artista eliminado con 502. Eventos fue restaurado y los datos temporales fueron eliminados. Reportes en `.local/postman/hu3-java.log` y `.local/postman-real/` dentro del modulo.


## Recomendaciones (HU4)

`GET /usuarios/{id}/recomendaciones/por-preferencias` recomienda eventos futuros disponibles segun el catalogo de Eventos. Suma 5 puntos por artista favorito, 3 por tipo y 2 por ciudad de preferencia; cada criterio suma una vez. Ordena por puntos descendentes, fecha ascendente e ID ascendente; deduplica y devuelve motivos. Ver [REST.md](REST.md) para la regla completa, ejemplos y filtros.

Implementacion: `RecomendacionPreferenciasService`, DTOs de catalogo/recomendacion, `EventoClientFeign` y un reloj inyectable en la zona de Argentina. Reutiliza `UsuarioRepository` y la URL `MSV_EVENTO_URL`. Las consultas no escriben en MongoDB. Si Eventos no responde devuelve 503; usuario inexistente devuelve 404.

Desde la raiz, con el sistema iniciado: `.\msv-usuario\scripts\Test-Recomendaciones.ps1 -TestOutage`. Requiere al menos un evento futuro publicado con ciudad y participante en el catalogo real (se puede preparar con las pruebas de sistema existentes). Crea y elimina solo un cliente temporal; no modifica Eventos. Comprueba puntuacion, motivos, orden, filtros, vacio, 404, conservacion de preferencias y caida/recuperacion de Eventos por Gateway. El flag TestOutage detiene y restaura Eventos; usar solo en desarrollo.


Verificacion HU4 (16/11/2026): 101 pruebas Java de Usuarios y 132 solicitudes Postman con 480 aserciones, sin fallos. La prueba real por Gateway verifico coincidencias de 10 puntos, puntuacion/motivos frente al catalogo existente, orden, ausencia de duplicados, [] sin coincidencias, 404 y 503 durante una caida real de Eventos, con recuperacion y preferencias conservadas. No se modificaron eventos y el cliente temporal fue eliminado. Eventos quedo restaurado. Reportes: `.local/postman/hu4-java.log`, `.local/postman-real/results.json` y `.local/recomendaciones-result.json`.


## Compras en comun (HU5)

`GET /usuarios/{id}/recomendaciones/por-compras` consulta candidatos agregados en Compras y valida su disponibilidad con Eventos. Cuenta otros clientes distintos, excluye el historial propio y no expone compras individuales. Ver [REST.md](REST.md) para estados, reintegros parciales, motivos y orden.

Configurar `MSV_COMPRAS_URL` (local: http://localhost:8003; Docker: http://compras:8003). La falta de Compras produce 503 solo para esta funcionalidad; los endpoints de perfil y preferencias siguen independientes.

Prueba real desde la raiz: `.\msv-usuario\scripts\Test-ComprasEnComun.ps1 -TestOutage`. Requiere tres eventos publicados a mas de cuatro dias con sectores CAMPO y al menos seis cupos libres por evento, y un entorno local sin trafico concurrente. Crea clientes temporales y pagos simulados; comprueba conteos, privacidad, reintegros y recuperacion. Al terminar devuelve todas sus entradas, libera reservas, verifica inventario y elimina sus perfiles temporales. Los registros de compras/devoluciones se conservan para auditoria, ya excluidos del historial valido. No cancela ni edita eventos existentes.


Verificacion HU5 (16/11/2026): 117 pruebas Java de Usuarios, 74 de Compras y 11 de Gateway (202, sin fallos ni omitidas). Postman: 136 solicitudes y 492 aserciones sin fallos. La prueba MySQL real confirmo compras multiples, entradas repetidas y varios eventos comunes sin duplicar vecinos, reintegros parciales/totales y del evento compartido, exclusiones propias, privacidad y 503/recuperacion de Compras y Eventos. Limpieza correcta: entradas de prueba reintegradas, inventario restituido y perfiles temporales eliminados. Reportes: `.local/compras-en-comun-result.json` del modulo y `.local/system-tests/hu5-java-summary.json` en la raiz.

## Recomendaciones unificadas (HU6)

`GET /usuarios/{id}/recomendaciones?limite=10` combina las dos fuentes mediante `RecomendacionService`. Devuelve `{recomendaciones, parcial, fuentesNoDisponibles, historialVerificado, disponibilidadVerificada}` (indicadores agregados en HU8/HU9). La lista original de HU4 se consulta ahora en `/por-preferencias`; `/por-compras` conserva su respuesta. Actualizar los consumidores del endpoint principal para leer el nuevo objeto.

Puntuacion: gustos (0 a 10) mas dos puntos por comprador en comun, hasta otros 10. Se unen motivos y fuentes por ID, se ordena por puntos descendentes, fecha e ID ascendentes y se limita al final (1 a 100). Sin una fuente se utiliza la otra; una indisponibilidad produce 200 parcial si la otra consulta funciona, aunque este vacia. Ambas fuentes inaccesibles producen 503. Ver [REST.md](REST.md) y OpenAPI 1.6.0 para el contrato completo.

Prueba real: `.\msv-usuario\scripts\Test-ComprasEnComun.ps1 -TestUnificadas -TestOutage`. Reutiliza las compras simuladas y su limpieza de HU5. Verifica union, puntuacion, motivos, limite, preferencias solas, historial solo, vacio y caidas/recuperacion de Compras y Eventos. Requiere el mismo entorno de desarrollo sin trafico concurrente y detiene brevemente esos servicios. Reporte: `.local/compras-en-comun-result.json` con `unifiedVerified`, `outageVerified` y `cleanupPassed`.

Verificacion HU6 (16/11/2026): 142 pruebas Java de Usuarios y 11 de Gateway, sin fallos ni omitidas. Postman: 148 solicitudes y 532 aserciones sin fallos. La prueba real con MongoDB/MySQL por Gateway confirmo la fusion de un evento presente en ambas fuentes, sus puntuaciones y motivos, las fuentes por separado, limites, 400/404, respuesta parcial con Compras caido, 503 con ambas fuentes inaccesibles y 200 parcial vacio cuando la otra fuente responde sin candidatos. Servicios restaurados, entradas temporales reintegradas, inventario restituido y perfiles temporales eliminados. Logs: `.local/postman/hu6-java.log` del modulo y `.local/system-tests/hu6-gateway.log`, `hu6-real.log`, `hu6-postman.log` de la raiz.

## Catalogo compartido (HU7)

HU7 centralizo los datos de Eventos y el instante de referencia para ambas fuentes. HU10 conserva la reutilizacion por solicitud y reemplaza la descarga del catalogo por una busqueda filtrada y un lote con los IDs faltantes. Los endpoints individuales pasan por la misma coordinacion; Compras sin candidatos sigue sin necesitar Eventos. Ver HU10 para la cantidad actual de llamadas y el rendimiento.

El contrato HTTP de HU6, las puntuaciones, motivos, orden y respuestas parciales se conservan. No hace falta cambiar la coleccion Postman para este cambio interno. Las pruebas de `RecomendacionIntegrationTests` cuentan las invocaciones a Eventos con ambas fuentes reales, verifican fallos compartidos y que la solicitud siguiente consulte datos nuevos. Ejecutar `./mvnw.cmd test` desde este modulo; reporte local `.local/postman/hu7-java.log`.

Verificacion HU7 (16/11/2026): 150 pruebas Java de Usuarios, 148 solicitudes Postman y 532 aserciones sin fallos. La prueba real `Test-ComprasEnComun.ps1 -TestUnificadas` comprobo puntuaciones, union, motivos, orden y limites con MongoDB/MySQL/Eventos por Gateway; entradas reintegradas, inventario restituido y perfiles temporales eliminados. Las caidas se verificaron con clientes Feign simulados en esta etapa; no se interrumpieron dependencias reales. Usuarios fue reconstruido y desplegado en Docker. Logs en `.local/system-tests/hu7-postman.log` y `hu7-real.log` de la raiz; informe en `.local/system-tests/hu7-real-result.json`.

## Excluir eventos comprados (HU8)

Compras proporciona el historial valido propio: eventos distintos con compras aprobadas y alguna entrada aun vigente. Usuarios aplica un filtro comun a ambas fuentes y a los endpoints individuales, antes de ordenar y limitar la lista unificada. El evento vuelve a ser candidato cuando se reintegran todas sus entradas, incluso si estaban repartidas en varias compras.

La respuesta unificada agrega `historialVerificado`. Si Compras no puede verificarlo, devuelve false y `parcial=true`; preferencias puede seguir aportando resultados, aunque no se puede garantizar la exclusion de eventos comprados. Los endpoints individuales conservan el array y expresan estos indicadores mediante `X-Historial-Verificado` y `X-Respuesta-Parcial`. Ver [REST.md](REST.md) y OpenAPI 1.7.0. Reimportar la coleccion Postman actualizada para validar el campo y las cabeceras.

Prueba real: `./msv-usuario/scripts/Test-ComprasEnComun.ps1 -TestUnificadas -TestOutage`. Ahora compra varias entradas propias del candidato recomendado, comprueba que desaparece, reintegra parcialmente y verifica que solo reaparece tras el reintegro total. Tambien verifica el modo parcial durante la caida de Compras. El informe agrega `historyVerified`; la limpieza reintegra todas las entradas temporales y verifica inventario.

Verificacion HU8 (16/11/2026): 157 pruebas Java de Usuarios, 85 de Compras y 12 de Gateway (254, sin fallos ni omitidas). Postman: 148 solicitudes y 538 aserciones sin fallos, incluido `historialVerificado` y las cabeceras de fuentes individuales. La prueba real MySQL/MongoDB/Eventos por Gateway verifico exclusiones y reintegros parciales/totales de varias compras del mismo evento, privacidad de la ruta interna, respuesta parcial con historial no verificado durante la caida de Compras, y recuperacion. Servicios restaurados; entradas temporales reintegradas, inventario restituido y perfiles temporales eliminados. Reporte estable: `.local/system-tests/hu8-real-result.json` en la raiz; logs `hu8-real.log`, `hu8-postman.log`, `hu8-compras-java.log` y `hu8-gateway-java.log`. Log Java de Usuarios: `.local/postman/hu8-java.log` del modulo.

## Entradas disponibles (HU9)

Usuarios consulta por Feign el inventario de Sectores/Asientos en un lote unico, solo para los candidatos no comprados. Excluye los agotados de ambas fuentes y de sus endpoints individuales antes del limite final. Los datos de Eventos se comparten por solicitud; desde HU10 se obtienen mediante busqueda filtrada y lote de IDs.

La respuesta unificada agrega `disponibilidadVerificada` y las fuentes individuales `X-Disponibilidad-Verificada`. Ante un fallo o contrato incompleto se conservan candidatos con disponibilidad no verificada y respuesta parcial. Sin candidatos no se consulta inventario; una respuesta vacia verificada no es un fallo. La recomendacion no reserva; la compra revalida al reservar. Ver [REST.md](REST.md) y OpenAPI 1.8.0; reimportar Postman para los nuevos campos.

Configurar `MSV_SECTORES_URL` (local: http://localhost:8002, Docker: http://sectores:8002). Prueba real desde la raiz: `./msv-usuario/scripts/Test-ComprasEnComun.ps1 -TestInventario -TestOutage`. Incluye las verificaciones de HU6/HU8, retiene temporalmente todos los cupos del candidato mediante reservas propias y comprueba que desaparece de ambas fuentes aunque el catalogo siga EN_CURSO. Al liberar una entrada reaparece. Tambien detiene/restaura Sectores y verifica disponibilidad no verificada. El informe agrega `inventoryVerified` e `inventoryOutageVerified`. Requiere un entorno de desarrollo sin trafico concurrente, con los mismos tres eventos de las pruebas anteriores y no mas de 50 entradas libres en el candidato. La limpieza libera sus reservas, reintegra compras y comprueba inventario.

Verificacion HU9 (16/11/2026): 164 pruebas Java de Usuarios, 43 de Sectores/Asientos y 12 de Gateway (219, sin fallos ni omitidas). Postman: 148 solicitudes y 538 aserciones sin fallos. La prueba real por Gateway con MongoDB/MySQL comprobo el lote, exclusion por reservas vigentes, reaparicion al liberar una entrada, caida/recuperacion de Sectores con disponibilidad no verificada en JSON y cabeceras, y las regresiones de HU6/HU8. Reservas temporales liberadas, entradas reintegradas, inventario restituido y perfiles temporales eliminados. Servicios restaurados; Usuarios y Sectores reconstruidos y desplegados. Reporte estable: `.local/system-tests/hu9-real-result.json` en la raiz; logs `hu9-real.log`, `hu9-postman.log`, `hu9-sectores-java.log`, `hu9-gateway-java.log` y `.local/postman/hu9-java.log` del modulo.

## Eventos necesarios para recomendar (HU10)

Usuarios ya no descarga `/api/eventos`. Pide candidatos por preferencias con OR (artista, tipo o ciudad normalizada) y obtiene los eventos de Compras y sus referencias por lote. Reutiliza los datos recibidos para consultar solo IDs faltantes: como maximo una busqueda y un lote, sin una llamada por evento. Sin criterios o IDs omite la consulta correspondiente. No limita en Eventos antes de combinar fuentes, calcular puntos y aplicar las exclusiones finales.

Se conservan puntuaciones, motivos, filtros, orden, JSON y cabeceras de HU8/HU9. Si falla una consulta de Eventos y la otra funciona, usa la fuente disponible con respuesta parcial. Ver [REST.md](REST.md), OpenAPI 1.9.0 de Usuarios y 1.2.0 de Eventos. El endpoint publico `/api/eventos/buscar` mantiene su combinacion AND y su paginacion; recomendaciones usa rutas internas nuevas.

Objetivo acordado: 100 consultas con 10.000 eventos y p95 <= 2 segundos. Prueba reproducible desde la raiz: `./tests/recomendaciones/Test-HU10-Rendimiento.ps1`. Levanta bases y contenedores temporales, mide por Gateway con proveedores reales, compara contra el baseline del catalogo completo y retira sus datos. Ver [procedimiento y alcance](../tests/recomendaciones/README.md). No mide carga concurrente ni todos los eventos coincidiendo a la vez.

Verificacion HU10 (16/11/2026): 168 pruebas Java de Usuarios, 11 de Eventos y 12 de Gateway (191, sin fallos ni omitidas). Postman: 148 solicitudes y 538 aserciones sin fallos. Las pruebas reales con Compras e inventario confirmaron puntuaciones, motivos, exclusiones, reintegros, caidas y recuperacion. Medicion final con 10.000 eventos: 60 candidatos por preferencias, tres sin cupos, 100 consultas secuenciales despues de 10 de calentamiento; p50 271,42 ms, p95 339,73 ms y maximo 399,78 ms. Cumple el objetivo de 2.000 ms; los diez resultados coinciden con el baseline completo. Proveedores reales, datos aislados, limpieza correcta. Informe `.local/system-tests/hu10-performance-result.json`, logs `hu10-performance.log`, `hu10-real.log`, `hu10-postman.log`, `hu10-eventos-java.log`, `hu10-gateway-java.log` en la raiz y `.local/postman/hu10-java.log` del modulo.
