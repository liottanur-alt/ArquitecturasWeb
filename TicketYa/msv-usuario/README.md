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

La coleccion [TicketYa-Usuarios.postman_collection.json](TicketYa-Usuarios.postman_collection.json) tiene 34 solicitudes. Reimportarla en Postman y ejecutar completa de 00 a 33 con `baseUrl=http://localhost:8004`, `eventosBaseUrl=http://localhost:8005` y `modoArtistas=real`. La solicitud 00 crea un artista temporal y captura su ID; la 33 lo elimina. No hace falta elegir un artista manualmente. Si se interrumpe, revisar los IDs capturados antes de limpiar sus datos. Evitar variables de entorno viejas que sobrescriban las URLs o el modo.

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


## Ultima verificacion de la coleccion

16/11/2026: dos recorridos completos contra Usuarios y Eventos reales, 68 solicitudes y 258 aserciones, sin fallos de solicitudes, scripts ni aserciones. Se validan campos completos y tipos, perfiles devueltos frente a lo enviado y guardado, roles, ID/nombre/genero exactos de los favoritos, ausencia de duplicados, estados de error y limpieza de los fixtures. El ejecutor usa el archivo opcional de datos si existe; si no, genera dos recorridos con valores unicos. Reporte local: `.local/postman-real/results.json`.
