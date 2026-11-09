# OpenAPI y pruebas de Usuarios con Postman

## Archivos de entrega

- `src/main/resources/static/openapi.json`: contrato OpenAPI 3.0.3 con las 13 operaciones de usuarios, roles y artistas favoritos, esquemas, parámetros, ejemplos y respuestas 400/404/409.
- `TicketYa-Usuarios.postman_collection.json`: 32 requests ordenados, con comprobaciones de estado, IDs, perfiles y favoritos.
- `datos-prueba.postman.json`: dos conjuntos de nombres y ciudades para Collection Runner. No contiene IDs de base ni contraseñas.

Con Usuarios iniciado, el contrato se descarga de http://localhost:8004/openapi.json. Esta ruta de documentación se consulta directamente al microservicio: el Gateway solo enruta las APIs de usuarios y roles. Se puede importar el JSON en Swagger Editor, Swagger UI o Postman; no se necesita JWT. El contrato es estático: si cambia la API debe actualizarse junto al código.

## Preparar Postman

1. Levantar MySQL de Usuarios y el servicio en 8004 siguiendo README.md.
2. Si se quiere probar el Gateway, iniciarlo en 8080 con destino Usuarios en 8004.
3. En Postman usar Import y seleccionar TicketYa-Usuarios.postman_collection.json.
4. En las variables de la colección, definir baseUrl sin barra final: http://localhost:8080 para Gateway o http://localhost:8004 para conexión directa.
5. Mantener No Auth. Configurar idArtistaPrueba con un ID existente en Eventos: el valor por defecto 900001 es ilustrativo. Usuarios no consulta Eventos al guardar favoritos, pero al listarlos obtiene nombre y genero desde ese servicio. Iniciar Eventos y configurar MSV_EVENTO_URL para ejecutar el recorrido completo; un artista inexistente devuelve 502 y un servicio no disponible devuelve 503.
6. No definir variables de entorno con los mismos nombres que las variables generadas (idCliente, idOrganizador, idRolCliente, idRolOrganizador, etc.): podrían ocultar los valores capturados. Solo baseUrl e idArtistaPrueba están pensadas para personalizarse.

## Ejecutar el recorrido

Seleccionar la colección completa y abrir Run collection. Conservar el orden 01 a 32. Opcionalmente seleccionar datos-prueba.postman.json como archivo de datos: el Runner hará dos iteraciones, una por conjunto de nombres/ciudad.

La primera solicitud crea un runId único, genera emails y DNI distintos y consulta los IDs reales de CLIENTE y ORGANIZADOR. Las siguientes solicitudes capturan los IDs de los usuarios creados. No se presupone que un rol o perfil tenga ID 1.

El recorrido comprueba:

1. Consulta de roles inicializados y de un rol individual.
2. Creación de cliente y organizador, respuesta 201 y Location.
3. Listado, consulta y actualización de ambos perfiles.
4. Actualización del rol CLIENTE al mismo nombre, sin cambiar su significado.
5. Alta de favorito, repetición sin duplicarlo, listado y eliminación.
6. Respuesta 409 ante email o DNI duplicado y rol duplicado/en uso.
7. Respuesta 400 ante datos ausentes, email inválido, rol sin tipo e ID de artista no positivo.
8. Respuesta 404 ante rol inexistente y usuario ya eliminado.
9. Eliminación del cliente con un favorito asociado, conservación del organizador y eliminación de ese organizador.
10. Conservación de los roles inicializados.

Se espera que todas las aserciones estén en verde. Una respuesta inesperada detiene el recorrido para no continuar usando IDs inválidos. Los datos de nombres/ciudad son reproducibles; los emails/DNI agregan un sufijo distinto para permitir repetir sin colisionar con ejecuciones anteriores.

## Limpieza y ejecución manual

Una ejecución completa borra los dos usuarios que crea. No elimina roles compartidos ni usuarios anteriores. Si se interrumpe, revisar las variables idCliente, idOrganizador y runId de la colección. Consultar GET /usuarios/{id} y comprobar que el email contiene ese runId antes de eliminar manualmente con DELETE /usuarios/{id}. No ejecutar borrados masivos.

Para usar Send solicitud por solicitud, comenzar por 01 y seguir el mismo orden. Ejecutar 01 otra vez genera un recorrido nuevo y descarta los IDs capturados previamente; si el anterior quedó interrumpido, limpiarlo antes.

## CRUD de roles

El OpenAPI documenta también POST /roles (201) y DELETE /roles/{id} (204). Al iniciar Usuarios ya se crean los cuatro tipos permitidos y sus nombres son únicos. Por eso, normalmente POST de un tipo válido devuelve 409: no es un error de la colección.

La colección prueba POST duplicado, PUT conservando el mismo tipo y DELETE rechazado porque está en uso. Para probar un alta y una baja exitosas de roles se necesita una base descartable con un tipo ausente y sin usuarios relacionados. No se elimina un rol global del entorno de trabajo solo para conseguir ese caso positivo.

## Errores documentados

Los errores de restricciones de la base usan ProblemDetail (normalmente application/problem+json); otros errores de Spring pueden usar application/json con status, error, path y timestamp. El contrato no promete un campo detail en todos los errores. La colección verifica el código HTTP y status sin depender de mensajes específicos o timestamps.

## Automatización con Newman

Si Node.js y npm están disponibles y el servicio ya está levantado:

```powershell
Set-Location 'C:/Users/franc/OneDrive/Escritorio/RefactorArqui/ArquitecturasWeb/TicketYa/msv-usuario'
npx.cmd --yes newman run ./TicketYa-Usuarios.postman_collection.json --env-var baseUrl=http://localhost:8004 --iteration-data ./datos-prueba.postman.json --bail
```

Para verificar el Gateway, cambiar baseUrl a http://localhost:8080 y levantarlo previamente. Newman necesita descargar su paquete si todavía no está en la caché de npm.

El Runner/Newman modifica la base de la URL seleccionada: crea y después elimina únicamente sus perfiles de prueba. Las pruebas automáticas Java de Usuarios siguen disponibles con .\mvnw.cmd test y utilizan H2.

## Verificación realizada

El 9 de noviembre de 2026 se validó el contrato con Swagger CLI y se ejecutó la colección con Newman 6.2.3 contra una instancia temporal real de Spring Boot en 18004, con una base H2 en memoria. Se utilizaron los dos conjuntos de datos: 64 requests y 126 aserciones, sin fallos. Se comprobó además que /openapi.json se sirve por HTTP. Al finalizar quedaron cero usuarios y se conservaron los cuatro roles. La instancia temporal se detuvo.

Esta ejecución verifica la colección y la API HTTP directa; no certifica el Gateway ni MySQL. No cambió la base MySQL del proyecto. El reporte técnico temporal queda en target/postman-resultados.json mientras no se ejecute Maven clean.
