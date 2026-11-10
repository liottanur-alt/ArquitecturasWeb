#!/bin/bash
set -euo pipefail

# Las contrasenas se escapan como literales SQL, sin interpretar barras invertidas.
escape_password() { printf '%s' "$1" | sed "s/'/''/g"; }
usuario_password=$(escape_password "$MYSQL_USUARIO_PASSWORD")
evento_password=$(escape_password "$MYSQL_EVENTO_PASSWORD")
sector_password=$(escape_password "$MYSQL_SECTOR_PASSWORD")

# Solo se ejecuta al inicializar un volumen vacio. Cada aplicacion tiene su propio esquema.
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot <<SQL
SET SESSION sql_mode = 'NO_BACKSLASH_ESCAPES';
CREATE DATABASE IF NOT EXISTS usuarios CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS eventos CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS sectores_asientos CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'usuario_app'@'%' IDENTIFIED BY '$usuario_password';
CREATE USER IF NOT EXISTS 'evento_app'@'%' IDENTIFIED BY '$evento_password';
CREATE USER IF NOT EXISTS 'sector_app'@'%' IDENTIFIED BY '$sector_password';

GRANT ALL PRIVILEGES ON usuarios.* TO 'usuario_app'@'%';
GRANT ALL PRIVILEGES ON eventos.* TO 'evento_app'@'%';
GRANT ALL PRIVILEGES ON sectores_asientos.* TO 'sector_app'@'%';
SQL
