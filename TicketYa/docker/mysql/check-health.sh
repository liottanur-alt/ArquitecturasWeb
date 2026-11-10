#!/bin/bash
set -euo pipefail

# No declara healthy hasta poder conectar con las tres cuentas a sus respectivos esquemas.
MYSQL_PWD="$MYSQL_USUARIO_PASSWORD" mysql --protocol=TCP -h127.0.0.1 -uusuario_app -Dusuarios -Nse 'SELECT 1' >/dev/null
MYSQL_PWD="$MYSQL_EVENTO_PASSWORD" mysql --protocol=TCP -h127.0.0.1 -uevento_app -Deventos -Nse 'SELECT 1' >/dev/null
MYSQL_PWD="$MYSQL_SECTOR_PASSWORD" mysql --protocol=TCP -h127.0.0.1 -usector_app -Dsectores_asientos -Nse 'SELECT 1' >/dev/null
