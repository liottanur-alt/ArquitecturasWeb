# Docker en TicketYa



El sistema usa cinco aplicaciones, MongoDB y MySQL: siete contenedores en total.

## Qué hace cada archivo

| Archivo | Función |
| --- | --- |
| docker-compose.yml, en la raíz | Arranca las aplicaciones, MongoDB, MySQL, la red y los volumenes de datos. |
| Dockerfile, uno en cada módulo | Compila esa aplicación con Java 21 y prepara su imagen. |
| .dockerignore, uno en cada módulo | Excluye archivos innecesarios de la construcción. |
| msv-SectoerAsiento/compose.mysql-test.yml | MySQL temporal de pruebas en 3309, separado del desarrollo. |

El Compose de la raiz usa Eventos real para consultar artistas. El Compose de `msv-usuario/compose.yml` es una alternativa aislada con simulador; detener uno antes de iniciar el otro. Ambos conservan Usuarios en el mismo volumen MongoDB.

## Comandos desde la raíz

```powershell
# Construir y arrancar todo
docker compose up --build -d --wait --wait-timeout 600

# Ver estado y registros
docker compose ps
docker compose logs --tail 50 compras

# Reconstruir una aplicación después de cambiar código
docker compose up --build -d compras

# Usar solo MySQL para trabajar con IntelliJ
docker compose stop gateway compras usuarios eventos sectores
docker compose up -d --wait mysql mongodb

# Detener conservando datos
docker compose stop
```

MySQL publica 3306 y conserva los esquemas SQL en ticketya_mysql_data; el esquema usuarios anterior queda como legado sin uso. MongoDB publica 27017 y guarda Usuarios en ticketya-usuarios-local_usuarios_mongo. Dentro de Docker se usan mysql:3306 y mongodb:27017; desde el equipo, localhost.

`docker compose down` elimina contenedores y red, pero conserva los datos. Agregar `-v` elimina el volumen y sus datos.

Las credenciales de desarrollo están en [MySQL](docker/mysql/README.md). Se pueden personalizar copiando .env.mysql.example a .env antes de crear el volumen; cambiar ese archivo no actualiza contraseñas ya guardadas en MySQL.

## Variables de autenticación

Antes de arrancar el Compose, copiar `.env.example` a `.env` y completar:

| Variable | Uso |
| --- | --- |
| `JWT_SECRET` | Secreto HS256 compartido por Usuarios, Gateway, Eventos, Sectores y Compras para firmar y validar JWT de usuarios y de servicio. Mínimo 32 bytes y estable entre reinicios. |
| `ADMIN_SEED_PASSWORD` | Clave del administrador de prueba creado por el perfil dev de Usuarios. No dejar la clave por defecto `1234`. |

Si también se personalizan credenciales MySQL, agregar al mismo `.env` las variables de `.env.mysql.example`. Compose falla de inmediato con un mensaje claro si falta `JWT_SECRET` o `ADMIN_SEED_PASSWORD`. Usuarios no tiene secreto JWT por defecto; la clave `1234` del administrador de prueba solo se aplica si se ejecuta la aplicación fuera de Compose sin definir `ADMIN_SEED_PASSWORD`.

Estas variables, junto con `JWT_SECRET`, deben estar también en la configuración de ejecución de IntelliJ de cada aplicación iniciada fuera de Compose. Las pruebas Java usan un secreto propio de test, reemplazable mediante `JWT_SECRET`; ningún perfil principal incluye un secreto por defecto.

Ver [arranque rápido](GUIA-ARRANQUE.md), [puertos](Guia.md), [migración de datos anteriores](docker/mysql/README.md) y [pruebas](tests/system/README.md).
