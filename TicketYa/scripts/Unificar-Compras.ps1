#requires -Version 7.3
# Ejecutar antes de iniciar aplicaciones con la nueva configuracion compartida.
[CmdletBinding()]
param([string]$ContenedorCompras)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
$PSNativeCommandArgumentPassing = 'Standard'
$rootMigration = Split-Path -Parent $PSScriptRoot
$composeMigration = Join-Path $rootMigration 'docker-compose.yml'
function Docker-Checked([string[]]$Arguments) {
    $result = & docker @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Fallo Docker. Se conservan origen, volumen y respaldos; no reiniciar aplicaciones hasta revisar.' }
    return $result
}
function Root-Query([string]$Container, [string]$Sql) {
    Docker-Checked -Arguments @('exec',$Container,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot -N -B -e "$1"','sh',$Sql)
}
function Wait-Root([string]$Container) {
    for ($attemptMigration=0; $attemptMigration -lt 60; $attemptMigration++) {
        & docker exec $Container sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot -Nse "SELECT 1"' 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { return }
        Start-Sleep -Seconds 1
    }
    throw 'MySQL no quedo disponible; no se importaron datos.'
}
$destination = ((Docker-Checked -Arguments @('compose','-f',$composeMigration,'ps','-q','mysql')) -join '').Trim()
if (-not $destination) { throw 'Iniciar primero el MySQL compartido existente; no iniciar aplicaciones.' }
if (-not $ContenedorCompras) {
    $sources = @(Docker-Checked -Arguments @('ps','-a','--filter','label=com.docker.compose.service=mysql-compra','--format','{{.ID}}') | Where-Object { $_ })
    if ($sources.Count -ne 1) { throw 'Indicar -ContenedorCompras: debe existir exactamente un origen mysql-compra.' }
    $ContenedorCompras = $sources[0]
}
$source = ((Docker-Checked -Arguments @('inspect','--format','{{.Id}}',$ContenedorCompras)) -join '').Trim()
$destination = ((Docker-Checked -Arguments @('inspect','--format','{{.Id}}',$destination)) -join '').Trim()
if ($source -eq $destination) { throw 'Origen y destino no pueden coincidir.' }
$tablesDestination = [int]((Root-Query $destination "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='compra_entrada_sol'") -join '')
if ($tablesDestination -ne 0) { throw 'El destino de Compras ya tiene tablas; no se sobrescribe ni se vuelve a importar.' }
# Bloquear escrituras desde todas las aplicaciones durante respaldo, copia y verificacion.
Docker-Checked -Arguments @('compose','-f',$composeMigration,'stop','gateway','compras','usuarios','eventos','sectores') | Out-Null
Docker-Checked -Arguments @('start',$source) | Out-Null
Wait-Root $source
$tablesSource = @(Root-Query $source "SELECT table_name FROM information_schema.tables WHERE table_schema='compra_entrada_sol' AND table_type='BASE TABLE' ORDER BY table_name")
if ($tablesSource.Count -eq 0) { throw 'El origen no tiene tablas de Compras; revisar antes de migrar.' }
$backupDir = Join-Path $rootMigration ('.local/backups/unificar-compras-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $backupDir | Out-Null
$manifestMigration = @()
foreach ($backupMigration in @(@{container=$source; name='compras'; databases=@('compra_entrada_sol')}, @{container=$destination; name='compartida'; databases=@('usuarios','eventos','sectores_asientos')})) {
    $tempDump = '/tmp/ticketya-unificar-' + [guid]::NewGuid().ToString('N') + '.sql'
    $hostDump = Join-Path $backupDir ($backupMigration.name + '.sql')
    Docker-Checked -Arguments (@('exec',$backupMigration.container,'sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; file="$1"; shift; exec mysqldump --protocol=socket -uroot --single-transaction --routines --events --triggers --no-tablespaces --set-gtid-purged=OFF --result-file="$file" --databases "$@"','sh',$tempDump) + $backupMigration.databases) | Out-Null
    Docker-Checked -Arguments @('cp',"$($backupMigration.container):$tempDump",$hostDump) | Out-Null
    if ((Get-Item $hostDump).Length -eq 0) { throw 'Respaldo vacio; se cancela.' }
    $manifestMigration += @{name=$backupMigration.name; source=$backupMigration.container; sha256=(Get-FileHash $hostDump -Algorithm SHA256).Hash}
}
$manifestMigration | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $backupDir 'manifest.json') -Encoding utf8
# Recrear mysql para recibir MYSQL_COMPRA_PASSWORD; el volumen existente se conserva.
Docker-Checked -Arguments @('compose','-f',$composeMigration,'up','-d','mysql') | Out-Null
$destination = ((Docker-Checked -Arguments @('compose','-f',$composeMigration,'ps','-q','mysql')) -join '').Trim()
Wait-Root $destination
# Init no se ejecuta automaticamente sobre un volumen existente.
Docker-Checked -Arguments @('exec',$destination,'bash','/docker-entrypoint-initdb.d/01-create-databases.sh') | Out-Null
$tempImport = '/tmp/ticketya-import-' + [guid]::NewGuid().ToString('N') + '.sql'
Docker-Checked -Arguments @('cp',(Join-Path $backupDir 'compras.sql'),"${destination}:$tempImport") | Out-Null
Docker-Checked -Arguments @('exec',$destination,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --protocol=socket -uroot < "$1"','sh',$tempImport) | Out-Null
$verificationMigration = @()
foreach ($tableMigration in $tablesSource) {
    if ($tableMigration -notmatch '^[a-zA-Z0-9_]+$') { throw 'Nombre de tabla inesperado; revisar manualmente.' }
    $sqlMigration = "SELECT COUNT(*) FROM compra_entrada_sol.$tableMigration; CHECKSUM TABLE compra_entrada_sol.$tableMigration EXTENDED;"
    $sourceData = (Root-Query $source $sqlMigration) -join "`n"
    $destinationData = (Root-Query $destination $sqlMigration) -join "`n"
    if ($sourceData -ne $destinationData) { throw "No coincide contenido de $tableMigration. Mantener aplicaciones detenidas y revisar respaldos." }
    $verificationMigration += @{table=$tableMigration; result=$destinationData}
}
$verificationMigration | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $backupDir 'verification.json') -Encoding utf8
Docker-Checked -Arguments @('exec',$destination,'bash','/opt/ticketya/check-health.sh') | Out-Null
# Detener origen solo despues de verificar filas y checksums. No se elimina su volumen.
Docker-Checked -Arguments @('stop',$source) | Out-Null
Write-Host "Migracion verificada: $($tablesSource.Count) tablas. Respaldos: $backupDir"
Write-Host 'Origen detenido y volumen conservado. Ejecutar docker compose up --build -d --wait --wait-timeout 600.'
