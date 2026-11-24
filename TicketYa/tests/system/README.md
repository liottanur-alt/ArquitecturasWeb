# Pruebas de TicketYa

Requieren PowerShell 7 y el sistema Docker saludable. El Gateway exige JWT, por lo que los scripts toman `ADMIN_SEED_PASSWORD` del entorno o de `.env` (el mismo valor usado por Compose). Ejecutar desde la raíz:

```powershell
docker compose up --build -d --wait --wait-timeout 600
pwsh -NoProfile -File ./tests/system/Test-System.ps1
pwsh -NoProfile -File ./tests/system/Test-System.ps1 -AuditSecurity
pwsh -NoProfile -File ./tests/system/Test-Load.ps1 -Concurrency 10 -Requests 500
pwsh -NoProfile -File ./tests/system/Test-Concurrencia-MultiInstancia.ps1
```

| Prueba | Qué comprueba |
| --- | --- |
| Integración | Servicios, MongoDB, MySQL y Redis reales por Gateway con token: evento e inventario, compra idempotente, pago, QR, devolución y recorrido de fila de alta demanda hasta admitir al siguiente comprador. Para este último crea `FILA_MAX_COMPRADORES_SIMULTANEOS + 1` clientes de prueba; las identidades y los eventos se conservan para inspección. |
| Seguridad | Con `-AuditSecurity`, comprueba que Gateway bloquea lectura anónima, token inválido, ruta interna y suplantación por `X-Organizador-Id`. |
| Carga | 500 consultas autenticadas de catálogo, disponibilidad y compras, con hasta 10 trabajadores simultáneos. |
| Concurrencia multi-instancia | 30 compras paralelas con claves distintas repartidas entre dos Gateway, dos Compras y dos Sectores aislados: overselling, duplicados, idempotencia cruzada, pagos y consistencia del inventario. |

`Test-System.ps1` inicia sesión con la cuenta de administrador de prueba, registra usuarios nuevos mediante `/auth/registro` y les asigna los roles ORGANIZADOR, ADMINISTRADOR y PERSONAL_ACCESO con el token de administrador. Conserva las credenciales del cliente de prueba en el informe local para que `Test-Load.ps1` pueda obtener su propio token; no reutiliza secretos de despliegue. La integración crea datos nuevos identificables y los conserva para inspección y carga. No borra los existentes. Usar una base de desarrollo respaldada; los pagos y reintegros son simulados.

## Resultados y códigos de salida

- 0: prueba correcta.
- 1: fallo funcional, de infraestructura o de los umbrales de carga.
- 2: integración correcta, pero alguno de los controles de seguridad falló.

Con `-AuditSecurity`, `securityPassed` es true solo cuando los cinco controles devuelven 401 o 403. No se esperan hallazgos abiertos en el Gateway.

Los informes quedan en .local/system-tests/integration-security.json y load.json, excluidos de Git. El primero separa passed (integración), securityPassed y overallPassed; `fixture.auth` contiene credenciales temporales de prueba.

La carga se ejecuta después de una integración correcta. Mide p50, p95, máximo, errores y solicitudes/s. Pasa si p95 <= 2000 ms y errores <= 1%, globalmente y por endpoint. Se pueden ajustar -Requests, -Concurrency, -MaxP95Ms y -MaxErrorRate.

Ambos scripts aceptan -BaseUrl y -OutputDirectory. `Test-System.ps1` también acepta -AdminEmail y -AdminPassword; carga también acepta -FixturePath. El fixture debe pertenecer al mismo servidor. La medición es local, de lectura y con carga cerrada; no mide compras concurrentes ni capacidad de producción. La ultima ejecucion (21/11/2026, 500 solicitudes y 10 trabajadores) surgio 0 errores, p50 55,2836 ms, p95 168,0607 ms, maximo 523,4679 ms y 53,2923979502806 solicitudes/s; ver [resultados](RESULTADOS.md).

`Test-Concurrencia-MultiInstancia.ps1` usa `compose.multinstance.yml` con prefijo GUID: dos Gateway, dos Compras, dos Sectores, Eventos y Usuarios con bases y JWT propios, sin volúmenes ni contacto con el catálogo principal. Reparte las compras entre ambos Gateway con un JWT de CLIENTE, comprueba en los logs que las dos JVM de Compras recibieron trafico, reenvía las claves por el Gateway opuesto y valida pagos e inventario. Acepta -Capacity, -Requests y -OutputDirectory; por defecto 10 cupos y 30 compras. Informe `.local/system-tests/concurrent-multi.json`. La ultima ejecucion (21/11/2026) termino `passed=true`, `cleanupPassed=true`, 8 comprobaciones, `201:10 / 409:20`, 0 5xx y trafico 15/15 por JVM.

## Colecciones Postman y scripts funcionales autenticados

Las colecciones Postman originales apuntan a puertos directos y no envian JWT. Para ejecutarlas sobre el Gateway se usaron copias temporales en `C:\Users\Nur\AppData\Local\Temp\opencode\postman-phase3\` con `auth=bearer`, `baseUrl`/`eventosBaseUrl=http://127.0.0.1:8080` y entornos temporales con JWT. No se editaron las copias de Compras ni Sectores: usan IDs fijos y mutan datos existentes. En Usuarios se omitieron cuatro solicitudes de roles compartidos. Eventos paso 46/46 solicitudes y 87 aserciones; Usuarios 70/70 y 254 aserciones, ambas con 0 fallos. La coleccion de Eventos del repositorio se ajusto para crear inventario antes de publicar, para las expectativas reales de cancelacion idempotente y sector protegido, y para usar bearer `{{jwtToken}}` con `baseUrl=http://127.0.0.1:8080` (el token se inyecta desde el entorno temporal); no se cambio codigo de produccion.

```powershell
$d='C:\Users\Nur\AppData\Local\Temp\opencode\postman-phase3'
npx.cmd --yes newman run "$d\Eventos-auth.json" --environment "$d\admin.postman_environment.json" --reporters cli --timeout-request 30000
npx.cmd --yes newman run "$d\Usuarios-auth.json" --environment "$d\admin.postman_environment.json" --reporters cli --timeout-request 30000
```

Los scripts de `msv-usuario/scripts/` tambien se ejecutaron desde copias temporales JWT/Gateway en `C:\Users\Nur\AppData\Local\Temp\opencode\phase4\` (`Test-Recomendaciones.ps1`, `Test-Preferencias.ps1`, `Test-Artistas-Reales.ps1` y `Test-Api.ps1` mediante `run-phase4-api.ps1`). Los cuatro terminaron con `EXITCODE=0`; se omitio unicamente la comprobacion que intentaba borrar el rol CLIENTE compartido. No se usaron modificadores de caida de servicios. Los informes quedan en Temp; la verificacion posterior no encontro fixtures residuales.

La carga de consultas con varias instancias usa el mismo `compose.multinstance.yml` aislado, pero es un arnes temporal fuera del repositorio. Resultado mixto: una corrida de 600 solicitudes paso con p95 1195,2887 ms y la repeticion supero el umbral con p95 2079,4963 ms (recomendaciones 2626,3706 ms), siempre con 0 errores y limpieza correcta. No se considera aprobada; el detalle esta en [resultados](RESULTADOS.md).

Las suites Java se ejecutan con `./mvnw.cmd test` desde cada módulo, o `sh ./mvnw test` en Linux/macOS. No hay POM agregador raiz. La construcción Docker compila las pruebas pero omite su ejecución. Usuarios requiere MongoDB disponible: su suite utiliza una base temporal aislada y la elimina al finalizar. Gateway usa un secreto de prueba propio y no necesita Docker.

Para recomendaciones unificadas (HU6), ejecutar `./msv-usuario/scripts/Test-ComprasEnComun.ps1 -TestUnificadas -TestOutage`. Requiere tres eventos publicados a mas de cuatro dias con sectores CAMPO y al menos seis cupos libres, en desarrollo sin trafico concurrente. Crea compras simuladas, verifica la union y la degradacion parcial, detiene/restaura Compras y Eventos, reintegra sus entradas y comprueba el inventario original. El informe queda en `msv-usuario/.local/compras-en-comun-result.json`; debe indicar `passed`, `unifiedVerified`, `outageVerified` y `cleanupPassed` en true. Ver [contrato y puntuacion](../../msv-usuario/REST.md).

Desde HU8, esa misma prueba verifica la exclusion por compras propias y el regreso del evento tras reintegrar todas las entradas de varias compras. Tambien debe indicar `historyVerified=true`. Comprueba `historialVerificado=false` y respuesta parcial durante la caida de Compras; la reutilizacion de datos de HU7 y las consultas acotadas de HU10 siguen cubiertas por la suite Java de Usuarios.

Para HU9 ejecutar `./msv-usuario/scripts/Test-ComprasEnComun.ps1 -TestInventario -TestOutage`. Incluye las pruebas anteriores y agrega el lote de disponibilidad: retiene temporalmente todos los cupos del candidato mediante reservas propias, verifica su exclusion de ambas fuentes y libera una entrada para comprobar que reaparece. Requiere no mas de 50 entradas libres en el candidato y el entorno sin trafico concurrente. Detiene/restaura Sectores, Compras y Eventos; durante la caida de Sectores verifica disponibilidad no verificada en JSON y por cabeceras a traves de Gateway. El informe debe agregar `inventoryVerified=true` e `inventoryOutageVerified=true`. La limpieza libera reservas y reintegra todas sus entradas, sin borrar registros ajenos.

Para HU10, el mismo recorrido comprueba las nuevas consultas filtradas y por IDs con Compras real. La medicion de escala acordada se ejecuta por separado con `./tests/recomendaciones/Test-HU10-Rendimiento.ps1`: 100 solicitudes autenticadas secuenciales por Gateway, 10.000 eventos, calentamiento de 10 solicitudes y p95 <= 2.000 ms. Usa bases y contenedores temporales con un secreto JWT generado por ejecución y compara datos, puntos, motivos, exclusiones y orden con el baseline completo; no introduce esos eventos en el catalogo principal. Ver [procedimiento y limites](../recomendaciones/README.md). Informe `.local/system-tests/hu10-performance-result.json`.

[Resultados verificados](RESULTADOS.md) · [Arranque](../../GUIA-ARRANQUE.md)
