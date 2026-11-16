# Arranque rápido de TicketYa

Actualizado: 14/11/2026. Ejecutar los comandos desde la raíz, donde está docker-compose.yml.

## Todo con Docker

Abrir Docker Desktop con contenedores Linux y ejecutar:

```powershell
docker compose up --build -d --wait --wait-timeout 600
docker compose ps
```

Deben aparecer seis contenedores saludables: Gateway, Usuarios, Eventos, Sectores, Compras y MySQL. La primera construcción descarga dependencias y puede tardar varios minutos. No hace falta instalar Java para esta opción.

Comprobar en el navegador:

- http://localhost:8080/actuator/health
- http://localhost:8080/roles
- http://localhost:8080/api/eventos

El puerto de MySQL es 3306 para los cuatro esquemas. Ver [cuentas y credenciales](docker/mysql/README.md).

## Java desde IntelliJ

Requiere JDK 21 o superior. Detener primero los contenedores de las aplicaciones si estaban ejecutándose:

```powershell
docker compose stop gateway compras usuarios eventos sectores
docker compose up -d --wait mysql
```

Abrir los cinco módulos en IntelliJ y ejecutar sus clases Application: Usuarios, Eventos, SectorAsiento, Compras y Gateway. Iniciar Compras cuando Eventos y Sectores estén disponibles, y Gateway al final.

También se puede ejecutar `./mvnw.cmd spring-boot:run` en una terminal por módulo; en Linux/macOS usar `sh ./mvnw spring-boot:run`.

Las aplicaciones locales usan localhost:3306; dentro de Docker usan mysql:3306. Si IntelliJ conserva DB_URL con un puerto anterior, quitarla o actualizarla. Los puertos HTTP están en [Guia.md](Guia.md).

## Probar y detener

Los pasos de integración, seguridad y carga están en [pruebas de sistema](tests/system/README.md). Las colecciones Postman y contratos REST están dentro de cada módulo.

```powershell
docker compose stop
```

Esto detiene el sistema y conserva los datos. Para volver a iniciarlo, repetir el comando de arranque.

## Problemas frecuentes

| Problema | Revisar |
| --- | --- |
| Docker no responde | Docker Desktop debe estar iniciado. |
| Puerto ocupado | Detener la misma aplicación en IntelliJ o el contenedor que lo ocupa. |
| Un servicio no arranca | Consultar `docker compose logs --tail 50 NOMBRE`. |
| Instalación anterior con bases separadas | Seguir la [migración](docker/mysql/README.md) antes de iniciar las aplicaciones. |

La etapa actual no requiere token. Los pagos y reintegros son simulados; la [auditoría](tests/system/RESULTADOS.md) documenta las limitaciones de seguridad.
