# Revisión y pendientes de TicketYa

Fecha: 14 de noviembre de 2026.

## Actualización: comunicación Feign de Eventos

Los resultados de la revisión inicial que siguen se conservan como historial. Posteriormente se completaron `SectorEventoClient` y `SectorEventoResponseDTO`, y se configuraron tiempos máximos para Eventos → SectorAsiento. Eventos compila y sus 5 pruebas pasan, incluyendo comunicación HTTP real del cliente Feign con un proveedor local y rechazo de publicación ante falta de sectores o errores remotos. Su suite ahora usa H2 aislado, sin depender de MySQL de desarrollo. Se actualizaron también las rutas del proveedor de pruebas de inventario. La verificación con servicios reales y MySQL sigue pendiente.

Se revisaron configuración, documentación, controladores, servicios y contratos entre los cinco módulos. Se ejecutó Maven `-B -o -f <modulo>/pom.xml test` con OpenJDK 26.0.2 y Maven 3.10.0, usando dependencias locales. No se modificó la implementación.

## Resultado de las pruebas

| Módulo | Resultado de esta revisión |
| --- | --- |
| Usuarios | 18 pruebas correctas, sin fallas ni errores. |
| SectorAsiento | 37 ejecutadas: 25 correctas, 2 fallas de aserción y 10 errores. |
| Compras / Entradas / Solicitudes | 61 pruebas correctas, sin fallas ni errores. |
| Gateway | 10 pruebas correctas, sin fallas ni errores. |
| Eventos | No compila: 4 errores por referencias a `SectorEventoClient` y `SectorEventoResponseDTO` ausentes. No llega a ejecutar pruebas. |

Balance: 126 pruebas ejecutadas, 114 correctas, 2 fallas y 10 errores; Eventos queda fuera de ese conteo porque falla antes de las pruebas. El proyecto completo no está funcionando de forma verificable en su estado actual.

Docker está instalado, pero su motor Linux no está disponible (`dockerDesktopLinuxEngine` no existe). No se inició MySQL ni se ejecutó el circuito completo contra servicios y bases reales. Las suites H2 y los proveedores simulados no certifican ese circuito.

Los registros de esta ejecución quedan en `.local/revision-2026-11-14/<modulo>.log` (carpeta local ignorada por Git). Los primeros intentos sin permisos de acceso al caché Maven fallaron antes de probar código; los resultados anteriores corresponden a la ejecución posterior con ese acceso habilitado.

## Pendientes priorizados

### 1. Resuelto: completar las dependencias de código de Eventos

`msv-evento/src/main/java/msvevento/service/EventoService.java` importa `msvevento.client.SectorEventoClient` y `msvevento.dto.SectorEventoResponseDTO`, pero esos archivos no están presentes en el código fuente revisado. Maven confirmó cuatro errores de compilación por estas referencias. Completar el cliente Feign y el DTO con el contrato real de SectorAsiento, incluyendo ruta, precio, capacidad y tratamiento de errores. Comprobar que una compilación desde fuentes funciona y que se puede publicar un borrador con sectores configurados.

### 2. Resuelto: actualizar y recuperar las pruebas de SectorAsiento

Actualización: las 37 pruebas pasan tras adaptar el proveedor HTTP y las aserciones. También se incorporó `feign-hc5`: el transporte anterior no permitía completar las llamadas PATCH de sincronización de agotamiento. Eventos y SectorAsiento suman ahora 42 pruebas correctas. Registros: `.local/revision-2026-11-14/msv-evento-feign.log` y `.local/revision-2026-11-14/sectores-hc5.log`. A continuación se conserva el diagnóstico inicial.

El cliente `EventoClientFeign` consulta `/api/eventos/interno/{id}` y agregó `PATCH /api/eventos/interno/{id}/disponibilidad`. Sin embargo:

- `FeignClientsTests` sigue esperando dos operaciones y las rutas anteriores; ahora encuentra tres.
- El servidor de `InventarioIntegrationTests` intenta convertir todo lo que sigue a `/api/eventos/` en un número. Con `interno/{id}` falla y termina produciendo un 503 en el consumidor.
- Una aserción esperaba 404 y recibió 503; diez escenarios fallaron al preparar el inventario.

Actualizar el proveedor simulado y las aserciones al contrato vigente; agregar respuestas para la actualización de disponibilidad. Ejecutar nuevamente las 37 pruebas, incluyendo concurrencia, vencimientos y devoluciones. Estas fallas se reprodujeron con H2 y un servidor HTTP local; no se explican por Docker apagado.

### 3. Parcialmente resuelto: aislar y ampliar las pruebas de Eventos

La revisión inicial encontró únicamente `contextLoads`, sin perfil aislado. Ya se agregó H2 exclusivo para pruebas y cobertura de publicación con Feign. Sigue pendiente ampliar la cobertura de creación, edición de borradores, autorización de organizador, búsqueda, agotamiento, finalización y cancelación.

### 4. Alta: verificar el circuito real con MySQL

Iniciar Docker y las bases según `GUIA-ARRANQUE.md`, levantar los cinco servicios y probar: creación de catálogo y borrador, configuración de inventario, publicación, reserva, pago simulado, confirmación, QR, devolución y cancelación. Verificar también el acceso por Gateway y la reventa después de una devolución. Usar datos de prueba identificables y comprobar compensaciones ante caída de un proveedor. Sigue pendiente en esta revisión.

### 5. Alta: coordinar cancelaciones entre servicios

`EventoService.cancelarEvento` modifica únicamente el evento. Compras tiene una orquestación separada en `POST /compras/eventos/{id}/cancelacion`. Una cancelación directa en Eventos no dispara ese flujo de reintegros e inventario. Unificar el punto de entrada o implementar entrega persistente de eventos con reintentos y consumidores idempotentes. Verificar que una caída intermedia permita recuperar el proceso sin reintegros duplicados.

### 6. Alta para una etapa con autenticación: proteger roles y endpoints internos

La etapa actual funciona sin token. El encabezado `X-Organizador-Id` identifica al organizador pero no acredita su identidad. Hay operaciones de suspensión, cancelación y eliminación sin esa comprobación, y el Gateway enruta también `/api/eventos/interno/**` por la ruta general. Al incorporar autenticación, obtener la identidad de credenciales verificadas, validar propiedad y roles en cada operación y restringir llamadas internas. Probar explícitamente respuestas 401/403 y acceso entre organizadores.

### 7. Media: validar entradas y normalizar errores de Eventos

`EventoRequestDTO` no declara restricciones y los controladores reciben el cuerpo sin `@Valid`. La búsqueda pasa `pagina` y `tamanio` directamente a `PageRequest.of`. Incorporar validaciones de campos obligatorios, identificadores, coordenadas y paginación; responder 400 de forma consistente ante parámetros inválidos en vez de dejar excepciones sin tratamiento específico. Agregar casos de cuerpo vacío, IDs nulos, página negativa y tamaño cero.

### 8. Media: actualizar documentación y contratos

La guía todavía enumera las rutas de catálogo del Gateway como pendientes, aunque `api-gateway/src/main/resources/application.yml` ya las configura. También informa cantidades históricas de pruebas y describe el contrato anterior de Eventos usado por inventario. Actualizar guía, README, OpenAPI y colecciones Postman después de recuperar las suites. Revisar referencias a `futura jwt`, que no aparece dentro de esta carpeta del proyecto.

### 9. Media: automatizar migraciones y verificación conjunta

Versionar cambios de esquema con migraciones y evitar depender de `ddl-auto=update` fuera de desarrollo. La guía incluye una corrección manual de un índice de entradas para permitir reventa: convertirla en una migración comprobable. Incorporar un comando o pipeline que compile y pruebe los cinco módulos, con pruebas MySQL descartables y pruebas de contrato entre proveedores y consumidores.

### 10. Futuro: capacidades adicionales

Evaluar notificaciones persistentes a compradores, integración de pagos reales, pruebas de carga con varias instancias y despliegue conjunto de aplicaciones. Los pagos y reintegros actuales son simulados; tratarlos como tal al evaluar el alcance funcional. Estas propuestas no son fallas reproducidas de las suites.
