# Resultados verificados — 14/11/2026

Configuración actual: cinco aplicaciones y un MySQL con cuatro esquemas. Los seis contenedores quedaron saludables.

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
