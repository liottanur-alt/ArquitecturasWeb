#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [switch]$AuditSecurity,
    [string]$OutputDirectory = "$PSScriptRoot/../../.local/system-tests"
)
. "$PSScriptRoot/Common.ps1"
$BaseUrl = $BaseUrl.TrimEnd('/')
$checks = [System.Collections.Generic.List[string]]::new()
$findings = [System.Collections.Generic.List[object]]::new()
$fixture = @{}
$report = @{ startedAt = [DateTimeOffset]::UtcNow.ToString('o'); baseUrl = $BaseUrl; checks = $checks; findings = $findings; fixture = $fixture; passed = $false }
try {
    Invoke-Api GET '/actuator/health' | Out-Null
    $roles = @(Invoke-Api GET '/roles')
    $tag = [guid]::NewGuid().ToString('N')
    foreach ($roleName in @('CLIENTE', 'ORGANIZADOR', 'ADMINISTRADOR')) {
        $role = $roles | Where-Object name -eq $roleName | Select-Object -First 1
        if ($null -eq $role -and $roleName -eq 'ADMINISTRADOR') {
            $role = $roles | Where-Object name -eq 'ADMIN' | Select-Object -First 1
        }
        if ($null -eq $role) { throw "No existe el rol $roleName" }
        $fixture[$roleName] = Invoke-Api POST '/usuarios' @{
            nombre = "Prueba $roleName"; email = "$roleName-$tag@example.com"; dni = [guid]::NewGuid().ToString('N').Substring(0,20); ciudad = 'Tandil'; idRol = $role.id
        } @{} @(201)
    }
    $clientId = $fixture.CLIENTE.id
    $ownerId = $fixture.ORGANIZADOR.id
    $place = Invoke-Api POST '/api/lugares' @{ nombre = "Teatro $tag"; direccion = 'Prueba 123'; ciudad = 'Tandil'; latitud = -37.3288; longitud = -59.1367; tipo = 'TEATRO' } @{} @(201)
    $artist = Invoke-Api POST '/api/artistas' @{ nombre = "Artista $tag"; genero = 'Rock' } @{} @(201)
    $event = Invoke-Api POST '/api/eventos' @{
        nombre = "Integracion $tag"; descripcion = 'Fixture automatizado'; tipo = 'RECITAL'; idOrganizador = $ownerId
        participantesIds = @($artist.id); fechaHora = [DateTime]::UtcNow.AddDays(10).ToString('yyyy-MM-ddTHH:mm:ss'); idLugar = $place.id; demanda = $false
    } @{} @(201)
    $fixture.eventId = $event.id
    $eventId = $event.id
    $checks.Add('Creacion de datos por Gateway en Usuarios y Eventos')
    # Un borrador no debe aparecer en el catalogo publico.
    Invoke-Api GET "/api/eventos/$eventId" $null @{} @(404) | Out-Null
    Invoke-Api PATCH "/api/eventos/$eventId/publicar" $null @{'X-Organizador-Id' = "$clientId"} @(403) | Out-Null
    Invoke-Api PATCH "/api/eventos/$eventId/publicar" $null @{} @(400) | Out-Null
    $checks.Add('Borrador privado, organizador ajeno rechazado y encabezado obligatorio')
    $sector = Invoke-Api POST '/sectores' @{ idLugar = $place.id; nombre = "Campo $tag"; tipo = 'CAMPO'; capacidadTotal = 10 } @{} @(200,201)
    $sectorId = $sector.id
    Invoke-Api PUT "/eventos/$eventId/inventario" @{limitePorCliente = 4} @{} @(204) | Out-Null
    Invoke-Api PUT "/eventos/$eventId/sectores" @{idSector = $sectorId; precio = 1500; capacidadHabilitada = 10} | Out-Null
    Invoke-Api PATCH "/api/eventos/$eventId/publicar" $null @{'X-Organizador-Id' = "$ownerId"} | Out-Null
    $checks.Add('SectorAsiento consulta Eventos y Eventos valida inventario real al publicar')
    $availabilityPath = "/eventos/$eventId/sectores/$sectorId/disponibilidad"
    $fixture.availabilityPath = $availabilityPath
    $before = Invoke-Api GET $availabilityPath
    Assert-Equal $before.disponibles 10 'Capacidad inicial'
    $order = @{claveIdempotencia = "compra-$tag"; idCliente = $clientId; idEvento = $eventId; idSector = $sectorId; cantidadEntradas = 1; medioDePago = 'TARJETA'}
    $purchase = Invoke-Api POST '/compras' $order @{} @(201)
    $purchaseId = $purchase.id
    Assert-Equal $purchase.estadoCompra 'PENDIENTE' 'Compra pendiente'
    $again = Invoke-Api POST '/compras' $order @{} @(200,201)
    Assert-Equal $again.id $purchaseId 'Compra idempotente'
    $paid = Invoke-Api PUT "/compras/$purchaseId/pago?aprobado=true"
    Assert-Equal $paid.estadoCompra 'APROBADA' 'Pago aprobado'
    $tickets = @(Invoke-Api GET "/entradas/compra/$purchaseId")
    Assert-Equal $tickets.Count 1 'Una entrada emitida'
    $ticket = $tickets[0]
    $byQr = Invoke-Api GET "/entradas/qr/$([Uri]::EscapeDataString($ticket.qr))"
    Assert-Equal $byQr.id $ticket.id 'Consulta QR'
    $sold = Invoke-Api GET $availabilityPath
    Assert-Equal $sold.vendidos 1 'Compra confirmada en inventario real'
    Assert-Equal $sold.disponibles 9 'Descuento de disponibilidad'
    $checks.Add('Compra, reintento idempotente, pago simulado, QR e inventario entre servicios reales')
    Invoke-Api POST '/solicitudes' @{idEntrada = $ticket.id; idCliente = $ownerId; descripcion = 'Cliente ajeno'} @{} @(403) | Out-Null
    Invoke-Api POST '/compras' @{} @{} @(400) | Out-Null
    $checks.Add('Solicitud de otro cliente rechazada y compra invalida rechazada')
    if ($AuditSecurity) {
        # Expectativas de seguridad deliberadamente estrictas: nunca convertir una vulnerabilidad en un PASS.
        $probes = @(
            @{name = 'Lectura anonima de usuarios'; method = 'GET'; path = '/usuarios'; headers = @{}},
            @{name = 'Lectura anonima de compra ajena'; method = 'GET'; path = "/compras/$purchaseId"; headers = @{}},
            @{name = 'Token invalido aceptado'; method = 'GET'; path = "/compras/$purchaseId"; headers = @{Authorization = 'Bearer invalid'}},
            @{name = 'API interna accesible por Gateway'; method = 'GET'; path = "/api/eventos/interno/$eventId"; headers = @{}},
            @{name = 'Suplantacion por X-Organizador-Id'; method = 'GET'; path = '/api/eventos/mis-eventos'; headers = @{'X-Organizador-Id' = "$ownerId"}}
        )
        foreach ($probe in $probes) {
            $response = Invoke-WebRequest -Uri "$BaseUrl$($probe.path)" -Method $probe.method -Headers $probe.headers -SkipHttpErrorCheck -TimeoutSec 30
            $status = [int]$response.StatusCode
            $blocked = $status -in @(401,403) -or ($probe.name -eq 'API interna accesible por Gateway' -and $status -eq 404)
            $findings.Add(@{name = $probe.name; path = $probe.path; status = $status; passed = $blocked; severity = 'HIGH'})
        }
    }
    $request = Invoke-Api POST '/solicitudes' @{idEntrada = $ticket.id; idCliente = $clientId; descripcion = 'Devolucion automatizada'} @{} @(201)
    $resolution = @{idAdministrador = $fixture.ADMINISTRADOR.id; estadoSolicitud = 'ACEPTADA'}
    $refund = Invoke-Api PUT "/solicitudes/$($request.id)/resolver" $resolution
    Assert-Equal $refund.estadoReintegro 'COMPLETADO' 'Reintegro simulado'
    Invoke-Api PUT "/solicitudes/$($request.id)/resolver" $resolution | Out-Null
    $after = Invoke-Api GET $availabilityPath
    Assert-Equal $after.disponibles 10 'Inventario liberado tras reintegro'
    Assert-Equal $after.vendidos 0 'Sin entradas vendidas tras reintegro'
    $checks.Add('Devolucion, reintegro simulado idempotente y restitucion de inventario')
    $report.passed = $true
} catch {
    $report.error = $_.Exception.Message
    Write-Warning $_.Exception.Message
} finally {
    $report.securityPassed = if ($AuditSecurity) { $report.passed -and $findings.Count -eq 5 -and @($findings | Where-Object { -not $_.passed }).Count -eq 0 } else { $null }
    $report.overallPassed = $report.passed -and (-not $AuditSecurity -or $report.securityPassed)
    $report.finishedAt = [DateTimeOffset]::UtcNow.ToString('o')
    Save-Report $report "$OutputDirectory/integration-security.json"
    Write-Host "Resultado: $OutputDirectory/integration-security.json"
}
if (-not $report.passed) { exit 1 }
if (@($findings | Where-Object { -not $_.passed }).Count -gt 0) { exit 2 }
