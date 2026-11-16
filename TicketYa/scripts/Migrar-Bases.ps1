#requires -Version 7.3
# Migra datos de contenedores antiguos a esquemas vacios del MySQL compartido.
[CmdletBinding()]
param(
    [string]$ContenedorUsuarios,
    [string]$ContenedorEventos,
    [string]$ContenedorSectores,
    [switch]$DetenerOrigenes
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$PSNativeCommandArgumentPassing = 'Standard'
$raizMigracion = Split-Path -Parent $PSScriptRoot
$composeMigracion = Join-Path $raizMigracion 'docker-compose.yml'

function Invoke-DockerSeguro {
    param([string[]]$Arguments)
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Fallo un comando Docker; se interrumpe la migracion. No se eliminan los origenes.' }
}

function Buscar-Origen {
    param([string]$Nombre, [string]$Servicio)
    if ($Nombre) { return ((Invoke-DockerSeguro -Arguments @('inspect', '--format', '{{.Id}}', $Nombre)) -join '').Trim() }
    $ids = @(Invoke-DockerSeguro -Arguments @('ps', '-a', '--filter', "label=com.docker.compose.service=$Servicio", '--format', '{{.ID}}') | Where-Object { $_ })
    if ($ids.Count -gt 1) { throw "Hay varios origenes para $Servicio; indicar su contenedor por parametro." }
    if ($ids.Count -eq 0) { return $null }
    return ((Invoke-DockerSeguro -Arguments @('inspect', '--format', '{{.Id}}', $ids[0])) -join '').Trim()
}

function Consulta-Root {
    param([string]$Contenedor, [string]$Sql)
    # La clave se lee dentro del contenedor: no se imprime ni se escribe en argumentos del host.
    Invoke-DockerSeguro -Arguments @('exec', $Contenedor, 'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot -N -B -e "$1"', 'sh', $Sql)
}

Invoke-DockerSeguro -Arguments @('info', '--format', '{{.ServerVersion}}') | Out-Null
Invoke-DockerSeguro -Arguments @('compose', '-f', $composeMigracion, 'up', '-d', '--wait', 'mysql') | Out-Null
$destinoMigracion = ((Invoke-DockerSeguro -Arguments @('compose', '-f', $composeMigracion, 'ps', '-q', 'mysql')) -join '').Trim()
if (-not $destinoMigracion) { throw 'No se encontro el MySQL compartido.' }
$destinoMigracion = ((Invoke-DockerSeguro -Arguments @('inspect', '--format', '{{.Id}}', $destinoMigracion)) -join '').Trim()

$origenesMigracion = @(
    @{Base='usuarios'; Servicio='mysql-usuario'; Nombre=$ContenedorUsuarios},
    @{Base='eventos'; Servicio='mysql-evento'; Nombre=$ContenedorEventos},
    @{Base='sectores_asientos'; Servicio='mysql-sector'; Nombre=$ContenedorSectores}
)
$planesMigracion = @()
# Se revisan todos los destinos antes de importar el primero.
foreach ($origen in $origenesMigracion) {
    $idOrigen = Buscar-Origen -Nombre $origen.Nombre -Servicio $origen.Servicio
    if (-not $idOrigen) { Write-Host "Sin contenedor antiguo para $($origen.Base); se conserva su esquema nuevo vacio."; continue }
    if ($idOrigen -eq $destinoMigracion) { throw 'El origen y el destino no pueden ser el mismo contenedor.' }
    $baseMigracion = $origen.Base
    $tablasDestino = [int]((Consulta-Root $destinoMigracion "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$baseMigracion'") -join '')
    if ($tablasDestino -ne 0) { throw "El destino $baseMigracion ya tiene tablas. No se sobrescribe. No iniciar Spring Boot antes de migrar." }
    $activoOrigen = ((Invoke-DockerSeguro -Arguments @('inspect', '--format', '{{.State.Running}}', $idOrigen)) -join '').Trim()
    if ($activoOrigen -ne 'true') { Invoke-DockerSeguro -Arguments @('start', $idOrigen) | Out-Null }
    $listoOrigen = $false
    for ($intento = 0; $intento -lt 60; $intento++) {
        $saludOrigen = ((Invoke-DockerSeguro -Arguments @('inspect', '--format', '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}', $idOrigen)) -join '').Trim()
        if ($saludOrigen -eq 'healthy' -or $saludOrigen -eq 'running') { $listoOrigen = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $listoOrigen) { throw "No se pudo iniciar el origen $baseMigracion." }
    $existeOrigen = [int]((Consulta-Root $idOrigen "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$baseMigracion'") -join '')
    if ($existeOrigen -ne 1) { throw "No existe el esquema $baseMigracion en el origen indicado." }
    $planesMigracion += @{Base=$baseMigracion; Origen=$idOrigen}
}

if ($planesMigracion.Count -eq 0) { Write-Host 'No hay origenes para importar. Las bases compartidas estan disponibles.'; return }
$directorioRespaldo = Join-Path $raizMigracion ('.local/backups/mysql-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $directorioRespaldo | Out-Null
$manifiestoMigracion = @()
# Primero se respaldan todos los origenes; despues se comienza a importar.
foreach ($plan in $planesMigracion) {
    $nombreTemporal = '/tmp/ticketya-' + $plan.Base + '-' + [guid]::NewGuid().ToString('N') + '.sql'
    $archivoRespaldo = Join-Path $directorioRespaldo ($plan.Base + '.sql')
    Invoke-DockerSeguro -Arguments @('exec', $plan.Origen, 'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --protocol=socket -uroot --single-transaction --routines --events --triggers --no-tablespaces --set-gtid-purged=OFF --databases "$1" --result-file="$2"', 'sh', $plan.Base, $nombreTemporal) | Out-Null
    Invoke-DockerSeguro -Arguments @('cp', "$($plan.Origen):$nombreTemporal", $archivoRespaldo) | Out-Null
    if (-not (Test-Path -LiteralPath $archivoRespaldo) -or (Get-Item -LiteralPath $archivoRespaldo).Length -eq 0) { throw 'El respaldo no se genero correctamente.' }
    $plan.Archivo = $archivoRespaldo
    $plan.Temporal = $nombreTemporal
    $manifiestoMigracion += @{Base=$plan.Base; ContenedorOrigen=$plan.Origen; SHA256=(Get-FileHash -LiteralPath $archivoRespaldo -Algorithm SHA256).Hash}
}
$manifiestoMigracion | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directorioRespaldo 'manifest.json') -Encoding utf8

foreach ($plan in $planesMigracion) {
    # Se vuelve a revisar justo antes de importar para detectar un arranque accidental de Spring.
    $tablasDestino = [int]((Consulta-Root $destinoMigracion "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$($plan.Base)'") -join '')
    if ($tablasDestino -ne 0) { throw "El destino $($plan.Base) dejo de estar vacio. No se importa." }
    Invoke-DockerSeguro -Arguments @('cp', $plan.Archivo, "$($destinoMigracion):$($plan.Temporal)") | Out-Null
    Invoke-DockerSeguro -Arguments @('exec', $destinoMigracion, 'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot < "$1"', 'sh', $plan.Temporal) | Out-Null
    $conteoOrigen = [int]((Consulta-Root $plan.Origen "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$($plan.Base)'") -join '')
    $conteoDestino = [int]((Consulta-Root $destinoMigracion "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$($plan.Base)'") -join '')
    if ($conteoOrigen -ne $conteoDestino) { throw "No coincide el numero de tablas de $($plan.Base); revisar el respaldo antes de continuar." }
    $tablasMigradas = @(Consulta-Root $plan.Origen "SELECT table_name FROM information_schema.tables WHERE table_schema='$($plan.Base)' AND table_type='BASE TABLE' ORDER BY table_name")
    foreach ($tabla in $tablasMigradas) {
        $tablaEscapada = $tabla.Replace('`', '``')
        $consultaConteo = 'SELECT COUNT(*) FROM `{0}`.`{1}`' -f $plan.Base, $tablaEscapada
        $filasOrigen = [long]((Consulta-Root $plan.Origen $consultaConteo) -join '')
        $filasDestino = [long]((Consulta-Root $destinoMigracion $consultaConteo) -join '')
        if ($filasOrigen -ne $filasDestino) { throw "No coincide el numero de filas de $($plan.Base).$tabla; revisar el respaldo." }
    }
    Write-Host "Importado $($plan.Base): $conteoDestino tablas."
}
Write-Host "Respaldos conservados en $directorioRespaldo"
if ($DetenerOrigenes) {
    foreach ($origenDetener in @($planesMigracion | ForEach-Object { $_.Origen } | Select-Object -Unique)) {
        Invoke-DockerSeguro -Arguments @('stop', $origenDetener) | Out-Null
    }
    Write-Host 'Origenes detenidos despues de verificar tablas y filas; sus contenedores y volumenes se conservan.'
} else {
    Write-Host 'Los contenedores y volumenes antiguos NO se borraron ni se detuvieron. Se pueden detener despues de verificar los datos.'
}
