# Eventos: colección de Postman y datos de prueba

## Archivos

- `src/main/resources/static/openapi.json`: OpenAPI 3.0.3 de las 19 operaciones actuales.
- `TicketYa-Eventos.postman_collection.json`: 41 solicitudes ordenadas con aserciones automáticas y captura de IDs.
- `datos-prueba.postman.json`: dos iteraciones de ejemplo, para Tandil y Azul.

La documentación se sirve en http://localhost:8005/openapi.json cuando Eventos está iniciado. Puede importarse en Swagger Editor/Swagger UI o en Postman. La especificación es estática y debe actualizarse si cambia el código.

## Arranque y configuración

Desde la raíz del repositorio iniciar el MySQL compartido con `docker compose up -d --wait`. Después iniciar Eventos desde su carpeta: `.\mvnw.cmd spring-boot:run` en Windows o `sh ./mvnw spring-boot:run` en Linux/macOS, con JDK 21 o superior.

En Postman, importar la colección. Dejar Authorization en No Auth y baseUrl en http://localhost:8005, sin barra final. El catálogo de Eventos todavía no está en el Gateway: no usar 8080 salvo que posteriormente se incorporen esas rutas.

idOrganizadorPrueba tiene un valor externo ilustrativo (900001). Puede cambiarse por el ID real de un organizador, pero Eventos actualmente no consulta Usuarios ni comprueba su rol. El recorrido no requiere Usuarios ni SectorAsiento: no prueba una compra completa ni cancelaciones distribuidas.

## Ejecución

Usar Run collection y ejecutar los 41 requests en orden. Seleccionar opcionalmente datos-prueba.postman.json como archivo de datos para ejecutar las dos iteraciones. La primera solicitud genera un identificador único, nombres exclusivos y una fecha futura. Se capturan los IDs reales del lugar, artista y evento creados.

No definir variables de entorno con los nombres generados idLugar, idArtista, idEvento, runId, nombreLugarPrueba, nombreArtistaPrueba, nombreEventoPrueba o fechaEvento: podrían ocultar las variables capturadas. Personalizar únicamente baseUrl, idOrganizadorPrueba o los nombres/ciudad del archivo de datos.

Se comprueban:

1. CRUD de lugares y artistas con IDs generados.
2. Creación de evento como EN_CURSO/BORRADOR, incluso si el DTO intenta indicar otros estados.
3. Actualización del evento, relaciones anidadas con lugar/participantes y conservación de estados.
4. Búsqueda que excluye borradores.
5. Publicación y rechazo de publicación repetida.
6. Búsqueda publicada con tipo, organizador, artista, ciudad y rango de fechas; orden por fecha y nombre; paginación y resultados vacíos.
7. Errores 400 por enum, fecha, ID no numérico y ordenamiento inválidos.
8. Suspensión, exclusión de búsquedas y rechazo de suspensión/publicación repetidas incompatibles.
9. Cancelación y rechazo de cancelación repetida.
10. Eliminación del evento antes que sus relaciones, 404 posteriores y referencias de artista/lugar inexistentes.

No se prueba como 400 el envío de un objeto vacío a crear un evento: el código no tiene esa validación y puede devolver 500. Tampoco se prueba como 409 el borrado de lugares/artistas asociados: hoy esas restricciones pueden terminar en 500. Son limitaciones documentadas, no garantías inventadas por la colección.

## Limpieza

Una ejecución completa elimina solo el evento, artista y lugar que creó, en ese orden. No borra listas enteras ni registros previos. Una respuesta inesperada detiene el recorrido.

Si se interrumpe, revisar idEvento, idArtista, idLugar y runId en la colección. Consultar cada registro y verificar que su nombre incluya ese runId. Eliminar primero el evento propio y después su artista y lugar. No eliminar entidades ajenas para completar la prueba. Reiniciar por la solicitud 01 crea otra ejecución y reemplaza los IDs capturados: limpiar la anterior antes de hacerlo.

La fecha de ejemplo se genera con una cadena ISO sin offset, para un evento futuro; es un dato de prueba y no un instante UTC. El contrato de producción utiliza fecha/hora local del evento. No hay validación de fecha futura en el CRUD actual.

## Newman

Con el servicio ya iniciado, desde la carpeta msv-evento:

Windows:

```powershell
npx.cmd --yes newman run ./TicketYa-Eventos.postman_collection.json --env-var baseUrl=http://localhost:8005 --iteration-data ./datos-prueba.postman.json --bail
```

Linux/macOS:

```bash
npx --yes newman run ./TicketYa-Eventos.postman_collection.json --env-var baseUrl=http://localhost:8005 --iteration-data ./datos-prueba.postman.json --bail
```

Node.js/npm son necesarios solo para Newman. No hacen falta para importar y ejecutar la colección en Postman. Las pruebas usan la base de la URL seleccionada: crean y eliminan su fixture; no ejecutarlas contra un entorno que no se quiera modificar.

## Verificación realizada

El 9 de noviembre de 2026 se validó OpenAPI con Swagger CLI y se empaquetó Eventos correctamente. Se ejecutó Newman 6.2.3 contra una instancia temporal del JAR real de Spring Boot en 18005, con H2 en memoria añadido solo al proceso de prueba, sin cambiar dependencias ni lógica de producción.

Las dos iteraciones completaron 82 requests y 164 aserciones sin fallos. Se comprobó que /openapi.json se sirve por HTTP y que al finalizar no quedaron eventos, artistas ni lugares de prueba. La instancia temporal fue detenida. No se modificó la base MySQL. Esta comprobación no certifica el Gateway, MySQL ni la coordinación con Compras/SectorAsiento.

El reporte temporal de Newman queda en target/postman-resultados.json hasta ejecutar Maven clean. No es una prueba de carga ni reemplaza pruebas Java de negocio.
