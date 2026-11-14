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
| Integración | Servicios y MySQL reales por Gateway: evento, inventario, compra idempotente, pago simulado, QR y devolución del cupo. |
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

Las suites Java se ejecutan con `./mvnw.cmd test` desde cada módulo, o `sh ./mvnw test` en Linux/macOS. La construcción Docker omite su ejecución.

[Resultados verificados](RESULTADOS.md) · [Arranque](../../GUIA-ARRANQUE.md)
