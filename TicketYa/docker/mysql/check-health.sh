#!/usr/bin/env bash
set -euo pipefail

comprobar() {
    MYSQL_PWD="$3" mysql --protocol=TCP -h127.0.0.1 -u"$1" -D"$2" -Nse 'SELECT 1' >/dev/null
}

comprobar usuario_app usuarios "$MYSQL_USUARIO_PASSWORD"
comprobar evento_app eventos "$MYSQL_EVENTO_PASSWORD"
comprobar compra_app compra_entrada_sol "$MYSQL_COMPRA_PASSWORD"
comprobar sector_app sectores_asientos "$MYSQL_SECTOR_PASSWORD"
comprobar notificaciones_app notificaciones "$MYSQL_NOTIFICACIONES_PASSWORD"
