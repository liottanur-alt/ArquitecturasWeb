# Resultados vigentes - 16/11/2026

## Ultima verificacion: consultas filtradas y por lote (HU10)

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
