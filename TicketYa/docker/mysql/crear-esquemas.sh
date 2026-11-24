#!/usr/bin/env bash
set -euo pipefail

crear_esquema() {
    local esquema=$1 usuario=$2 clave=$3
    clave=${clave//\\/\\\\}
    clave=${clave//\'/\'\'}
    MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=TCP -h127.0.0.1 -uroot <<SQL
SET SESSION sql_mode = '';
CREATE DATABASE IF NOT EXISTS \`$esquema\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$usuario'@'%' IDENTIFIED BY '$clave';
GRANT ALL PRIVILEGES ON \`$esquema\`.* TO '$usuario'@'%';
SQL
}

crear_esquema usuarios usuario_app "$MYSQL_USUARIO_PASSWORD"
crear_esquema eventos evento_app "$MYSQL_EVENTO_PASSWORD"
crear_esquema compra_entrada_sol compra_app "$MYSQL_COMPRA_PASSWORD"
crear_esquema sectores_asientos sector_app "$MYSQL_SECTOR_PASSWORD"
crear_esquema notificaciones notificaciones_app "$MYSQL_NOTIFICACIONES_PASSWORD"
bash /opt/ticketya/check-health.sh
