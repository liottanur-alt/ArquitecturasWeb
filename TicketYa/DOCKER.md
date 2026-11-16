# Docker en TicketYa

El sistema usa cinco aplicaciones y un MySQL compartido: seis contenedores en total.

## Qué hace cada archivo

| Archivo | Función |
| --- | --- |
| docker-compose.yml, en la raíz | Arranca las aplicaciones, MySQL, la red y el volumen de datos. |
| Dockerfile, uno en cada módulo | Compila esa aplicación con Java 21 y prepara su imagen. |
| .dockerignore, uno en cada módulo | Excluye archivos innecesarios de la construcción. |
| msv-SectoerAsiento/compose.mysql-test.yml | MySQL temporal de pruebas en 3309, separado del desarrollo. |

Los Compose antiguos se retiraron. Para el uso habitual alcanza con el de la raíz.

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
docker compose up -d --wait mysql

# Detener conservando datos
docker compose stop
```

MySQL publica 3306 y guarda los cuatro esquemas en el volumen ticketya_mysql_data. Dentro de Docker las aplicaciones conectan a mysql:3306; desde el equipo, a localhost:3306.

`docker compose down` elimina contenedores y red, pero conserva los datos. Agregar `-v` elimina el volumen y sus datos.

Las credenciales de desarrollo están en [MySQL](docker/mysql/README.md). Se pueden personalizar copiando .env.mysql.example a .env antes de crear el volumen; cambiar ese archivo no actualiza contraseñas ya guardadas en MySQL.

Ver [arranque rápido](GUIA-ARRANQUE.md), [puertos](Guia.md), [migración de datos anteriores](docker/mysql/README.md) y [pruebas](tests/system/README.md).
