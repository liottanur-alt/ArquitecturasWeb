# Arranque rápido de TicketYa



Actualizado: 16/11/2026. Ejecutar los comandos desde la raíz, donde está docker-compose.yml.

## Todo con Docker

Copiar `.env.example` a `.env` y completar `JWT_SECRET` y `ADMIN_SEED_PASSWORD` antes del primer arranque. Abrir Docker Desktop con contenedores Linux y ejecutar:

```powershell
docker compose up --build -d --wait --wait-timeout 600
docker compose ps
```

Deben aparecer siete contenedores saludables: Gateway, Usuarios, Eventos, Sectores, Compras, MongoDB y MySQL. La primera construcción descarga dependencias y puede tardar varios minutos. No hace falta instalar Java para esta opción.

Comprobar en el navegador:

- http://localhost:8080/actuator/health
- http://localhost:8080/api/eventos

`/roles` y el resto de las rutas protegidas requieren `Authorization: Bearer <token>`. Para obtener un token con la cuenta de administrador de prueba:

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/auth/login `
  -ContentType application/json `
  -Body '{"email":"admin@admin.com","password":"CLAVE_DE_.ENV"}'
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
Invoke-RestMethod -Uri http://localhost:8080/roles -Headers $headers
```

Usuarios usa MongoDB en 27017. Eventos, Sectores y Compras usan MySQL en 3306. Ver [cuentas y credenciales](docker/mysql/README.md).

## Java desde IntelliJ

Requiere JDK 21 o superior. Detener primero los contenedores de las aplicaciones si estaban ejecutándose:

```powershell
docker compose stop gateway compras usuarios eventos sectores
docker compose up -d --wait mysql mongodb
```

Abrir los cinco módulos en IntelliJ y ejecutar sus clases Application: Usuarios, Eventos, SectorAsiento, Compras y Gateway. Iniciar Compras cuando Eventos y Sectores estén disponibles, y Gateway al final.

También se puede ejecutar `./mvnw.cmd spring-boot:run` en una terminal por módulo; en Linux/macOS usar `sh ./mvnw spring-boot:run`.

Usuarios local usa mongodb://localhost:27017/usuarios; en Docker usa mongodb://mongodb:27017/usuarios. Los otros servicios usan localhost:3306 o mysql:3306. Si IntelliJ conserva DB_URL con un puerto anterior, quitarla o actualizarla. Los puertos HTTP están en [Guia.md](Guia.md).

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

El Gateway valida JWT, bloquea las rutas internas y reemplaza `X-Organizador-Id` con el `sub` verificado. Eventos, Sectores y Compras también validan el token localmente y exigen un JWT de servicio en sus rutas `interno` y en las llamadas Feign reservadas. Los pagos y reintegros siguen siendo simulados. Ver la [política del Gateway](api-gateway/README.md) y los [resultados](tests/system/RESULTADOS.md).
