# Mejoras pendientes

Actualizado: 14/11/2026. El sistema arranca con seis contenedores; Compras ya usa el cuarto esquema del MySQL compartido. La integración real y la carga local de lectura pasaron. Ver [resultados](tests/system/RESULTADOS.md).

| Prioridad | Trabajo pendiente |
| --- | --- |
| Alta, para la etapa con autenticación | Incorporar identidad verificada y permisos por rol/propietario. Restringir endpoints internos y resolver los cinco hallazgos de la auditoría. |
| Alta | Comprobar recuperación ante caídas durante compras, devoluciones y cancelaciones, con reintentos sin duplicaciones. |
| Media | Ampliar validaciones y pruebas de Eventos: cuerpos inválidos, paginación, edición, búsqueda y cambios de estado. |
| Media | Versionar cambios de esquema con migraciones y automatizar la ejecución conjunta de las suites Maven. |
| Media | Medir compras concurrentes y carga con varias instancias. La carga actual solo mide consultas locales. |
| Futuro | Pagos reales, notificaciones persistentes y otras capacidades fuera de la etapa actual. |

Para cancelar un evento con sus compras e inventario, usar POST /compras/eventos/{id}/cancelacion. Cancelarlo directamente en Eventos no ejecuta toda la compensación de Compras.

La etapa actual sigue sin autenticación. Los pagos y reintegros son simulados.
