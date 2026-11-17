# Pruebas de TicketYa

Requieren PowerShell 7 y el sistema Docker saludable. Ejecutar desde la raíz:

```powershell
docker compose up --build -d --wait --wait-timeout 600
pwsh -NoProfile -File ./tests/system/Test-System.ps1
pwsh -NoProfile -File ./tests/system/Test-System.ps1 -AuditSecurity
pwsh -NoProfile -File ./tests/system/Test-Load.ps1 -Concurrency 10 -Requests 500
```

| Prueba | Qué comprueba |
| --- | --- |
| Integración | Servicios, MongoDB y MySQL reales por Gateway: evento, inventario, compra idempotente, pago simulado, QR y devolución del cupo. |
| Seguridad | Controles existentes de datos y pertenencia; con -AuditSecurity detecta acceso anónimo, token inválido, API interna expuesta y suplantación por ID. |
| Carga | 500 consultas de catálogo, disponibilidad y compras, con hasta 10 trabajadores simultáneos. |

La integración crea datos nuevos identificables y los conserva para inspección y carga. No borra los existentes. Usar una base de desarrollo respaldada; los pagos y reintegros son simulados.

## Resultados y códigos de salida

- 0: prueba correcta.
- 1: fallo funcional, de infraestructura o de los umbrales de carga.
- 2: integración correcta, pero la auditoría encontró vulnerabilidades.

La etapa actual sigue sin autenticación: se esperan cinco hallazgos de seguridad. Pasar la integración no significa que los endpoints estén protegidos.

Los informes quedan en .local/system-tests/integration-security.json y load.json, excluidos de Git. El primero separa passed (integración), securityPassed y overallPassed.

La carga se ejecuta después de una integración correcta. Mide p50, p95, máximo, errores y solicitudes/s. Pasa si p95 <= 2000 ms y errores <= 1%, globalmente y por endpoint. Se pueden ajustar -Requests, -Concurrency, -MaxP95Ms y -MaxErrorRate.

Ambos scripts aceptan -BaseUrl y -OutputDirectory. Carga también acepta -FixturePath; el fixture debe pertenecer al mismo servidor. La medición es local, de lectura y con carga cerrada; no mide compras concurrentes ni capacidad de producción.

Las suites Java se ejecutan con `./mvnw.cmd test` desde cada módulo, o `sh ./mvnw test` en Linux/macOS. La construccion Docker compila las pruebas pero omite su ejecucion. Usuarios requiere MongoDB disponible: su suite utiliza una base temporal aislada y la elimina al finalizar.

Para recomendaciones unificadas (HU6), ejecutar `./msv-usuario/scripts/Test-ComprasEnComun.ps1 -TestUnificadas -TestOutage`. Requiere tres eventos publicados a mas de cuatro dias con sectores CAMPO y al menos seis cupos libres, en desarrollo sin trafico concurrente. Crea compras simuladas, verifica la union y la degradacion parcial, detiene/restaura Compras y Eventos, reintegra sus entradas y comprueba el inventario original. El informe queda en `msv-usuario/.local/compras-en-comun-result.json`; debe indicar `passed`, `unifiedVerified`, `outageVerified` y `cleanupPassed` en true. Ver [contrato y puntuacion](../../msv-usuario/REST.md).

Desde HU8, esa misma prueba verifica la exclusion por compras propias y el regreso del evento tras reintegrar todas las entradas de varias compras. Tambien debe indicar `historyVerified=true`. Comprueba `historialVerificado=false` y respuesta parcial durante la caida de Compras; la reutilizacion de datos de HU7 y las consultas acotadas de HU10 siguen cubiertas por la suite Java de Usuarios.

Para HU9 ejecutar `./msv-usuario/scripts/Test-ComprasEnComun.ps1 -TestInventario -TestOutage`. Incluye las pruebas anteriores y agrega el lote de disponibilidad: retiene temporalmente todos los cupos del candidato mediante reservas propias, verifica su exclusion de ambas fuentes y libera una entrada para comprobar que reaparece. Requiere no mas de 50 entradas libres en el candidato y el entorno sin trafico concurrente. Detiene/restaura Sectores, Compras y Eventos; durante la caida de Sectores verifica disponibilidad no verificada en JSON y por cabeceras a traves de Gateway. El informe debe agregar `inventoryVerified=true` e `inventoryOutageVerified=true`. La limpieza libera reservas y reintegra todas sus entradas, sin borrar registros ajenos.

Para HU10, el mismo recorrido comprueba las nuevas consultas filtradas y por IDs con Compras real. La medicion de escala acordada se ejecuta por separado con `./tests/recomendaciones/Test-HU10-Rendimiento.ps1`: 100 solicitudes secuenciales por Gateway, 10.000 eventos, calentamiento de 10 solicitudes y p95 <= 2.000 ms. Usa bases y contenedores temporales y compara datos, puntos, motivos, exclusiones y orden con el baseline completo; no introduce esos eventos en el catalogo principal. Ver [procedimiento y limites](../recomendaciones/README.md). Informe `.local/system-tests/hu10-performance-result.json`.

[Resultados verificados](RESULTADOS.md) · [Arranque](../../GUIA-ARRANQUE.md)
