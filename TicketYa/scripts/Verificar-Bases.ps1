#requires -Version 7.3
# Comprueba disponibilidad y aislamiento de las tres cuentas de aplicacion.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$PSNativeCommandArgumentPassing = 'Standard'
$composeVerificacion = Join-Path (Split-Path -Parent $PSScriptRoot) 'docker-compose.yml'
& docker compose -f $composeVerificacion exec -T mysql bash /opt/ticketya/check-health.sh
if ($LASTEXITCODE -ne 0) { throw 'Una o varias bases no estan disponibles con sus cuentas de aplicacion.' }
$cuentasVerificacion = @(
    @{Usuario='usuario_app'; PasswordEnv='MYSQL_USUARIO_PASSWORD'; Base='usuarios'},
    @{Usuario='evento_app'; PasswordEnv='MYSQL_EVENTO_PASSWORD'; Base='eventos'},
    @{Usuario='sector_app'; PasswordEnv='MYSQL_SECTOR_PASSWORD'; Base='sectores_asientos'}
)
foreach ($cuenta in $cuentasVerificacion) {
    foreach ($otra in $cuentasVerificacion) {
        if ($cuenta.Base -eq $otra.Base) { continue }
        # Se prueban los dos accesos cruzados por cuenta; deben fallar por permisos.
        $sqlVerificacion = 'SHOW TABLES FROM ' + $otra.Base
        $resultadoCruzado = & docker compose -f $composeVerificacion exec -T mysql sh -c 'export MYSQL_PWD="$(printenv "$1")"; mysql --protocol=TCP -h127.0.0.1 -u"$2" -e "$3"' sh $cuenta.PasswordEnv $cuenta.Usuario $sqlVerificacion 2>&1
        if ($LASTEXITCODE -eq 0) { throw "La cuenta $($cuenta.Usuario) tiene acceso indebido a $($otra.Base)." }
        if (($resultadoCruzado -join "`n") -notmatch 'ERROR 1044') { throw 'El acceso cruzado fallo por otro motivo; no se puede certificar el aislamiento.' }
    }
}
Write-Host 'Tres esquemas disponibles y seis accesos cruzados rechazados.'
