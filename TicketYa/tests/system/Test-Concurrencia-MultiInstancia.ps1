#requires -Version 7.0
param(
    [int]$Capacity = 10,
    [int]$Requests = 30,
    [string]$OutputDirectory = "$PSScriptRoot/../../.local/system-tests"
)
$ErrorActionPreference = 'Stop'

$raiz = (Resolve-Path "$PSScriptRoot/../..").Path
$composeRaiz = Join-Path $raiz 'docker-compose.yml'
$compose = Join-Path $PSScriptRoot 'compose.multinstance.yml'
$tag = [guid]::NewGuid().ToString('N').Substring(0, 12)
$project = "ticketya-conc-$tag"
$dbEventos = "conc_${tag}_eventos"; $dbSectores = "conc_${tag}_sectores"
$dbCompras = "conc_${tag}_compras"; $dbUsuarios = "conc_${tag}_usuarios"
$cuenta = "conc_$tag"
if ($dbEventos -notmatch '^conc_[a-f0-9]{12}_eventos$' -or $dbSectores -notmatch '^conc_[a-f0-9]{12}_sectores$' -or
    $dbCompras -notmatch '^conc_[a-f0-9]{12}_compras$' -or $dbUsuarios -notmatch '^conc_[a-f0-9]{12}_usuarios$' -or
    $cuenta -notmatch '^conc_[a-f0-9]{12}$') { throw 'Nombres temporales invalidos' }

$variables = @{
    MC_RUN = $tag
    MC_EVENTOS_DB = $dbEventos; MC_SECTORES_DB = $dbSectores
    MC_COMPRAS_DB = $dbCompras; MC_USUARIOS_DB = $dbUsuarios
    MC_DB_USER = $cuenta; MC_DB_PASSWORD = "conc_test_$tag"
    MC_JWT_SECRET = "conc-secret-$tag-$tag"
    MC_ADMIN_SEED_PASSWORD = "conc_admin_$tag"
}
$anteriores = @{}
foreach ($nombre in $variables.Keys) {
    $anteriores[$nombre] = [Environment]::GetEnvironmentVariable($nombre, 'Process')
    [Environment]::SetEnvironmentVariable($nombre, $variables[$nombre], 'Process')
}
New-Item -ItemType Directory -Force $OutputDirectory | Out-Null
$salida = Join-Path $OutputDirectory 'concurrent-multi.json'
$checks = [Collections.Generic.List[string]]::new()
$report = @{
    startedAt = [DateTimeOffset]::UtcNow.ToString('o')
    mode = 'multi-instance'; project = $project; tag = $tag
    capacity = $Capacity; requests = $Requests
    instances = @{ gateway = 2; compras = 2; sectores = 2; eventos = 1; usuarios = 1 }
    datosAislados = $true; secureGateway = $true
    checks = $checks; passed = $false; cleanupPassed = $false
    cleanupErrors = [Collections.Generic.List[string]]::new()
}

function Sql([string]$Texto) {
    $Texto | & docker compose -f $composeRaiz exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4'
    if ($LASTEXITCODE -ne 0) { throw 'Fallo SQL en bases temporales de concurrencia' }
}
function Puerto([string]$Servicio, [int]$PuertoInterno) {
    $direccion = & docker compose -p $project -f $compose port $Servicio $PuertoInterno
    if ($LASTEXITCODE -ne 0) { throw "No se encontro el puerto temporal de $Servicio" }
    return "http://$($direccion.Trim())"
}
function Req([string]$Base, [string]$Method, [string]$Path, $Body = $null, [hashtable]$Headers = @{}) {
    $args = @{ Uri = "$Base$Path"; Method = $Method; Headers = $Headers; SkipHttpErrorCheck = $true; TimeoutSec = 90 }
    if ($null -ne $Body) { $args.ContentType = 'application/json; charset=utf-8'; $args.Body = $Body | ConvertTo-Json -Depth 10 -Compress }
    $r = Invoke-WebRequest @args
    $parsed = $null
    if ($r.Content) { try { $parsed = $r.Content | ConvertFrom-Json } catch { $parsed = $r.Content } }
    [pscustomobject]@{ status = [int]$r.StatusCode; body = $parsed; raw = $r.Content }
}
function Need([object]$Response, [int[]]$Expected, [string]$Message) {
    if ($Response.status -notin $Expected) { throw "$Message : esperado $($Expected -join '/'), recibido $($Response.status): $($Response.raw)" }
    return $Response.body
}
function Login([string]$Base, [string]$Email, [string]$Password) {
    $r = Invoke-RestMethod -Uri "$Base/auth/login" -Method Post -ContentType 'application/json' -Body (@{ email = $Email; password = $Password } | ConvertTo-Json)
    return $r.accessToken
}
function Contar-Inicio([string]$Servicio, [string]$Patron) {
    $lineas = & docker compose -p $project -f $compose logs $Servicio 2>&1 | Select-String -Pattern $Patron
    return @($lineas).Count
}

$baseA = $null; $baseB = $null
try {
    Sql "CREATE DATABASE $dbEventos CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE DATABASE $dbSectores CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE DATABASE $dbCompras CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '$cuenta'@'%' IDENTIFIED BY '$($variables.MC_DB_PASSWORD)'; GRANT ALL ON $dbEventos.* TO '$cuenta'@'%'; GRANT ALL ON $dbSectores.* TO '$cuenta'@'%'; GRANT ALL ON $dbCompras.* TO '$cuenta'@'%';"
    & docker compose -p $project -f $compose up -d --wait --wait-timeout 300
    if ($LASTEXITCODE -ne 0) { throw 'No se inicio el entorno multi-instancia temporal' }
    $baseA = Puerto gateway-a 8080
    $baseB = Puerto gateway-b 8080
    $report.baseGatewayA = $baseA
    $report.baseGatewayB = $baseB
    $checks.Add('Entorno aislado: 2 Gateway, 2 Compras, 2 Sectores, Eventos y Usuarios con bases y JWT propios')

    Need (Req $baseA GET '/actuator/health' $null @{}) @(200) 'salud gateway-a' | Out-Null
    Need (Req $baseB GET '/actuator/health' $null @{}) @(200) 'salud gateway-b' | Out-Null

    $seedHeaders = @{ Authorization = "Bearer $(Login $baseA 'admin@admin.com' $variables.MC_ADMIN_SEED_PASSWORD)" }
    $roles = @(Need (Req $baseA GET '/roles' $null $seedHeaders) @(200) 'roles')
    $orgRole = $roles | Where-Object name -eq 'ORGANIZADOR' | Select-Object -First 1
    if ($null -eq $orgRole) { throw 'No existe el rol ORGANIZADOR en el entorno temporal' }

    $password = "Conc-$tag!"
    $orgEmail = "ORG-$tag@example.com"
    $clientEmail = "CLIENTE-$tag@example.com"
    $org = Need (Req $baseA POST '/auth/registro' @{ nombre = "Org $tag"; email = $orgEmail; dni = "o$tag"; ciudad = 'Tandil'; password = $password }) @(201) 'registro organizador'
    $cliente = Need (Req $baseA POST '/auth/registro' @{ nombre = "Cliente $tag"; email = $clientEmail; dni = "c$tag"; ciudad = 'Tandil'; password = $password }) @(201) 'registro cliente'
    $cambio = @{ nombre = $org.nombre; email = $org.email; dni = $org.dni; ciudad = $org.ciudad; idRol = $orgRole.id }
    Need (Req $baseA PUT "/usuarios/$($org.id)" $cambio $seedHeaders) @(200) 'alto de rol ORGANIZADOR' | Out-Null
    $orgToken = Login $baseA $orgEmail $password
    $clientToken = Login $baseA $clientEmail $password
    $orgHeaders = @{ Authorization = "Bearer $orgToken" }
    $clientHeaders = @{ Authorization = "Bearer $clientToken" }
    $checks.Add('Usuarios aislados con roles derivados del JWT y autenticacion por Gateway')

    $place = Need (Req $baseA POST '/api/lugares' @{ nombre = "Lugar $tag"; direccion = 'Prueba 1'; ciudad = 'Tandil'; latitud = -37.3288; longitud = -59.1367; tipo = 'TEATRO' } $orgHeaders) @(201) 'lugar'
    $artist = Need (Req $baseA POST '/api/artistas' @{ nombre = "Artista $tag"; genero = 'Rock' } $orgHeaders) @(201) 'artista'
    $event = Need (Req $baseA POST '/api/eventos' @{
        nombre = "Concurrencia $tag"; descripcion = 'Prueba multi-instancia'; tipo = 'RECITAL'
        idOrganizador = $cliente.id; participantesIds = @($artist.id)
        fechaHora = [DateTime]::UtcNow.AddDays(10).ToString('yyyy-MM-ddTHH:mm:ss')
        idLugar = $place.id; demanda = $false
    } $orgHeaders) @(201) 'evento'
    $eventId = [long]$event.id
    $report.eventId = $eventId
    $sector = Need (Req $baseA POST '/sectores' @{ idLugar = $place.id; nombre = "Campo $tag"; tipo = 'CAMPO'; capacidadTotal = $Capacity } $orgHeaders) @(200, 201) 'sector'
    $sectorId = [long]$sector.id
    Need (Req $baseA PUT "/eventos/$eventId/inventario" @{ limitePorCliente = 50 } $orgHeaders) @(204) 'inventario' | Out-Null
    Need (Req $baseA PUT "/eventos/$eventId/sectores" @{ idSector = $sectorId; precio = 100; capacidadHabilitada = $Capacity } $orgHeaders) @(200) 'configuracion sector' | Out-Null
    Need (Req $baseA PATCH "/api/eventos/$eventId/publicar" $null $orgHeaders) @(200) 'publicacion' | Out-Null
    $availability = "/eventos/$eventId/sectores/$sectorId/disponibilidad"
    $before = Need (Req $baseA GET $availability $null $clientHeaders) @(200) 'disponibilidad inicial'
    if ($before.disponibles -ne $Capacity) { throw "Capacidad inicial inesperada: $($before.disponibles)" }
    $checks.Add('Evento, sector, inventario y publicacion creados por Gateway con JWT propio')

    # Las compras se reparten entre ambos Gateway/JVM con claves idempotentes distintas.
    $jobs = @()
    for ($i = 1; $i -le $Requests; $i++) {
        $base = if ($i % 2 -eq 1) { $baseA } else { $baseB }
        $jobs += [pscustomobject]@{
            target = $(if ($i % 2 -eq 1) { 'A' } else { 'B' }); base = $base; clave = "conc-$tag-$i"
            body = (@{ claveIdempotencia = "conc-$tag-$i"; idCliente = $cliente.id; idEvento = $eventId; idSector = $sectorId; cantidadEntradas = 1; medioDePago = 'TARJETA' } | ConvertTo-Json -Compress)
        }
    }
    $jobs = @($jobs | Sort-Object { Get-Random })
    $compras = @($jobs | ForEach-Object -Parallel {
        $job = $_
        try {
            $r = Invoke-WebRequest -Uri "$($job.base)/compras" -Method Post -Headers @{ Authorization = "Bearer $using:clientToken" } -ContentType 'application/json; charset=utf-8' -Body $job.body -SkipHttpErrorCheck -TimeoutSec 90
            $parsed = $null; if ($r.Content) { try { $parsed = $r.Content | ConvertFrom-Json } catch {} }
            [pscustomobject]@{ target = $job.target; clave = $job.clave; status = [int]$r.StatusCode; body = $parsed; raw = $r.Content }
        } catch {
            [pscustomobject]@{ target = $job.target; clave = $job.clave; status = 0; body = $null; raw = $_.Exception.Message }
        }
    } -ThrottleLimit $Requests)
    $created = @($compras | Where-Object status -eq 201)
    $conflicts = @($compras | Where-Object status -eq 409)
    $errores = @($compras | Where-Object { $_.status -ge 500 -or $_.status -eq 0 })
    $otros = @($compras | Where-Object { $_.status -ne 201 -and $_.status -ne 409 })
    $report.statusCounts = @($compras | Group-Object status | ForEach-Object { @{ status = [int]$_.Name; count = $_.Count } })
    $report.targetCounts = @($compras | Group-Object target | ForEach-Object { @{ target = $_.Name; total = $_.Count; created = @($_.Group | Where-Object status -eq 201).Count } })
    if ($created.Count -ne $Capacity) { throw "Se crearon $($created.Count) compras, se esperaban $Capacity" }
    if ($conflicts.Count -ne ($Requests - $Capacity)) { throw "Conflictos $($conflicts.Count), se esperaban $($Requests - $Capacity)" }
    if ($errores.Count -gt 0 -or $otros.Count -gt 0) { throw "Estados inesperados: $($errores.Count) errores, $($otros.Count) otros" }
    $ids = @($created | ForEach-Object { [long]$_.body.id } | Sort-Object -Unique)
    if ($ids.Count -ne $Capacity) { throw "IDs duplicados entre JVM: $($ids.Count) unicos" }
    $checks.Add("$Requests compras paralelas repartidas entre 2 Gateway: $Capacity aprobadas, $($conflicts.Count) rechazadas, 0 5xx, IDs unicos")

    $jvmA = Contar-Inicio 'compras-a' 'HTTP inicio POST /compras\s*$'
    $jvmB = Contar-Inicio 'compras-b' 'HTTP inicio POST /compras\s*$'
    $report.jvmPurchaseRequests = @{ comprasA = $jvmA; comprasB = $jvmB }
    if (@($compras | Where-Object target -eq 'A').Count -eq 0 -or @($compras | Where-Object target -eq 'B').Count -eq 0) {
        throw 'Las compras no se enviaron a ambos Gateway'
    }
    if ($jvmA -eq 0 -or $jvmB -eq 0) { throw "El trafico no llego a las dos JVM de Compras: A=$jvmA B=$jvmB" }
    $checks.Add("Trafico confirmado en dos JVM de Compras (logs: compras-a=$jvmA, compras-b=$jvmB)")

    $reserved = Need (Req $baseA GET $availability $null $clientHeaders) @(200) 'disponibilidad tras reservas'
    if ($reserved.disponibles -ne 0 -or $reserved.reservados -ne $Capacity -or $reserved.vendidos -ne 0) { throw "Inventario inconsistente tras reservas: $($reserved | ConvertTo-Json -Compress)" }
    $report.availabilityAfterReservations = $reserved
    $checks.Add("Sin overselling entre JVM: disponibles=0, reservados=$Capacity, vendidos=0")

    # Reenvio de las mismas claves por el Gateway opuesto: comprueba idempotencia en BD, no en memoria.
    $replayJobs = @($compras | ForEach-Object {
        $otro = if ($_.target -eq 'A') { $baseB } else { $baseA }
        [pscustomobject]@{ base = $otro; body = (@{ claveIdempotencia = $_.clave; idCliente = $cliente.id; idEvento = $eventId; idSector = $sectorId; cantidadEntradas = 1; medioDePago = 'TARJETA' } | ConvertTo-Json -Compress) }
    })
    $replay = @($replayJobs | ForEach-Object -Parallel {
        $job = $_
        try {
            $r = Invoke-WebRequest -Uri "$($job.base)/compras" -Method Post -Headers @{ Authorization = "Bearer $using:clientToken" } -ContentType 'application/json; charset=utf-8' -Body $job.body -SkipHttpErrorCheck -TimeoutSec 90
            $parsed = $null; if ($r.Content) { try { $parsed = $r.Content | ConvertFrom-Json } catch {} }
            [pscustomobject]@{ status = [int]$r.StatusCode; body = $parsed; raw = $r.Content }
        } catch { [pscustomobject]@{ status = 0; body = $null; raw = $_.Exception.Message } }
    } -ThrottleLimit $replayJobs.Count)
    $replayOk = @($replay | Where-Object { $_.status -in @(200, 201) -and [long]$_.body.id -in $ids })
    if ($replayOk.Count -ne $Capacity) { throw "Reenvio cruzado incorrecto: $($replayOk.Count)/$Capacity" }
    $afterReplay = Need (Req $baseA GET $availability $null $clientHeaders) @(200) 'disponibilidad tras reenvio'
    if ($afterReplay.reservados -ne $Capacity -or $afterReplay.disponibles -ne 0) { throw "El reenvio altero el inventario: $($afterReplay | ConvertTo-Json -Compress)" }
    $historial = @(Need (Req $baseB GET "/compras/cliente/$($cliente.id)" $null $clientHeaders) @(200) 'historial')
    $propias = @($historial | Where-Object claveIdempotencia -like "conc-$tag-*")
    if ($propias.Count -ne $Capacity) { throw "Historial con $($propias.Count) compras propias, se esperaban $Capacity" }
    $checks.Add("Reenvio concurrente cruzado de $Capacity claves: mismos IDs, sin duplicados ni cupos extra")

    $pagoJobs = @($ids | ForEach-Object -Begin { $n = 0 } -Process {
        $n++
        [pscustomobject]@{ base = $(if ($n % 2 -eq 1) { $baseA } else { $baseB }); path = "/compras/$_/pago?aprobado=true" }
    })
    $pagos = @($pagoJobs | ForEach-Object -Parallel {
        $job = $_
        try {
            $r = Invoke-WebRequest -Uri "$($job.base)$($job.path)" -Method Put -Headers @{ Authorization = "Bearer $using:clientToken" } -SkipHttpErrorCheck -TimeoutSec 90
            $parsed = $null; if ($r.Content) { try { $parsed = $r.Content | ConvertFrom-Json } catch {} }
            [pscustomobject]@{ status = [int]$r.StatusCode; body = $parsed; raw = $r.Content }
        } catch { [pscustomobject]@{ status = 0; body = $null; raw = $_.Exception.Message } }
    } -ThrottleLimit $pagoJobs.Count)
    $aprobadas = @($pagos | Where-Object { $_.status -eq 200 -and $_.body.estadoCompra -eq 'APROBADA' })
    if ($aprobadas.Count -ne $Capacity -or @($pagos | Where-Object status -ne 200).Count -gt 0) { throw "Pagos: $($aprobadas.Count) aprobados, $(@($pagos | Where-Object status -ne 200).Count) con error" }
    $rePago = Need (Req $baseB PUT "/compras/$($ids[0])/pago?aprobado=true" $null $clientHeaders) @(200) 'reintento de pago'
    if ($rePago.estadoCompra -ne 'APROBADA') { throw 'El pago duplicado no fue idempotente' }
    $sold = Need (Req $baseA GET $availability $null $clientHeaders) @(200) 'disponibilidad tras pagos'
    if ($sold.vendidos -ne $Capacity -or $sold.reservados -ne 0 -or $sold.disponibles -ne 0) { throw "Inventario inconsistente tras pagos: $($sold | ConvertTo-Json -Compress)" }
    $report.availabilityAfterPayments = $sold
    $entradas = @()
    foreach ($id in $ids) { $entradas += @(Need (Req $baseB GET "/entradas/compra/$id" $null $clientHeaders) @(200) 'entradas') }
    if ($entradas.Count -ne $Capacity) { throw "Entradas emitidas: $($entradas.Count), se esperaban $Capacity" }
    $checks.Add("$Capacity pagos paralelos en dos JVM y reintento: todos APROBADA, vendidos=$Capacity, sin duplicados")

    $report.purchaseIds = $ids
    $report.passed = $true
} catch {
    $report.error = $_.Exception.Message
    Write-Warning $_.Exception.Message
} finally {
    $errores = [Collections.Generic.List[string]]::new()
    if ($project) {
        & docker compose -p $project -f $compose down
        if ($LASTEXITCODE -ne 0) { $errores.Add('No se pudieron retirar los contenedores temporales') }
    }
    try { Sql "DROP DATABASE IF EXISTS $dbEventos; DROP DATABASE IF EXISTS $dbSectores; DROP DATABASE IF EXISTS $dbCompras; DROP USER IF EXISTS '$cuenta'@'%';" }
    catch { $errores.Add($_.Exception.Message) }
    & docker compose -f $composeRaiz exec -T mongodb mongosh --quiet --eval "db.getSiblingDB('$dbUsuarios').dropDatabase()" | Out-Null
    if ($LASTEXITCODE -ne 0) { $errores.Add('No se pudo retirar la base Mongo temporal') }
    foreach ($nombre in $variables.Keys) { [Environment]::SetEnvironmentVariable($nombre, $anteriores[$nombre], 'Process') }
    $report.cleanupErrors = @($errores.ToArray())
    $report.cleanupPassed = ($errores.Count -eq 0)
    $report.finishedAt = [DateTimeOffset]::UtcNow.ToString('o')
    $report | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $salida -Encoding utf8
    Write-Host "informe: $salida"
    Write-Host "passed=$($report.passed) cleanupPassed=$($report.cleanupPassed) errores=$($errores.Count) checks=$(@($checks).Count)"
    foreach ($e in $errores) { Write-Warning "cleanup: $e" }
}
if (-not $report.passed -or -not $report.cleanupPassed) { exit 1 }
