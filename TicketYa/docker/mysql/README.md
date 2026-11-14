# MySQL compartido

TicketYa usa MySQL 8.4 en localhost:3306, con cuatro esquemas independientes:

| Esquema | Usuario | Contraseña de desarrollo |
| --- | --- | --- |
| usuarios | usuario_app | usuario_dev |
| eventos | evento_app | evento_dev |
| sectores_asientos | sector_app | sector_dev |
| compra_entrada_sol | compra_app | compra_dev |

Cada cuenta accede solo a su esquema. Los datos se guardan en el volumen ticketya_mysql_data.

## Iniciar y comprobar

Desde la raíz del proyecto:

```powershell
docker compose up -d --wait mysql
pwsh -NoProfile -File ./scripts/Verificar-Bases.ps1
```

El script requiere PowerShell 7.3 o superior. Comprueba las cuatro cuentas y que se rechacen los doce accesos cruzados a esquemas ajenos.

Docker usa mysql:3306 y las aplicaciones en IntelliJ usan localhost:3306. La URL termina con el esquema, por ejemplo: jdbc:mysql://localhost:3306/compra_entrada_sol.

.env.mysql.example permite personalizar puerto y contraseñas. .env lo lee Compose; IntelliJ necesita sus propias variables. Si se cambia MYSQL_PORT, actualizar DB_URL en las aplicaciones locales. Una contraseña cambiada en .env también debe actualizarse en MySQL si el volumen ya existía. Para Compras, usar COMPRA_DB_PASSWORD en Compose y DB_PASSWORD en IntelliJ con el mismo valor.

## Instalar desde cero

Al crear un volumen vacío, el script init crea esquemas y cuentas. Las aplicaciones crean o actualizan sus tablas al arrancar. No hace falta ejecutar SQL manualmente.

## Migrar una instalación anterior

Estos pasos son solo para datos que todavía estén en contenedores separados. La instalación local actual ya fue migrada: no repetir la importación.

| Origen anterior | Script desde la raíz |
| --- | --- |
| Usuarios, Eventos y Sectores separados | `pwsh -File ./scripts/Migrar-Bases.ps1 -DetenerOrigenes` |
| Compras separado y MySQL compartido existente | `pwsh -File ./scripts/Unificar-Compras.ps1` |

Detener también las aplicaciones Java de IntelliJ antes de migrar. Unificar-Compras detiene las aplicaciones Docker automáticamente; Migrar-Bases requiere que ya estén detenidas. Conservar los contenedores de origen y mantener iniciado el MySQL compartido para unificar Compras.

Los scripts respaldan en .local/backups/ y rechazan destinos con tablas. El migrador de Compras compara filas y checksums antes de detener su origen. Si falla, revisar el destino y los respaldos antes de reintentar. No borran los volúmenes anteriores.

Después de una migración correcta:

```powershell
docker compose up --build -d --wait --wait-timeout 600
pwsh -NoProfile -File ./scripts/Verificar-Bases.ps1
```

Si el volumen compartido ya existía pero nunca hubo datos de Compras, iniciar solo mysql y ejecutar una vez `docker compose exec mysql bash /docker-entrypoint-initdb.d/01-create-databases.sh`. Crea el esquema y la cuenta faltantes; no cambia contraseñas existentes.

El antiguo contenedor mysql-compra quedó detenido y su volumen msv-compraentardasol_compra-data se conserva como respaldo. No se utiliza en el arranque actual. Volver a la configuración anterior requiere también restaurar las conexiones y el Compose anterior.

## Conservar los datos

Usar `docker compose stop` para detener. No usar `docker compose down -v` si se quieren conservar las bases o los respaldos anteriores.
