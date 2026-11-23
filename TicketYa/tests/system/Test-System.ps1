#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$AdminEmail = 'admin@admin.com',
    [string]$AdminPassword = $null,
    [switch]$AuditSecurity,
    [string]$OutputDirectory = "$PSScriptRoot/../../.local/system-tests"
)
. "$PSScriptRoot/Common.ps1"
$BaseUrl = $BaseUrl.TrimEnd('/')
if ([string]::IsNullOrWhiteSpace($AdminPassword)) { $AdminPassword = Get-ValorEntorno 'ADMIN_SEED_PASSWORD' }
$checks = [System.Collections.Generic.List[string]]::new()
$findings = [System.Collections.Generic.List[object]]::new()
$fixture = @{}
$tokens = @{}
$report = @{ startedAt = [DateTimeOffset]::UtcNow.ToString('o'); baseUrl = $BaseUrl; checks = $checks; findings = $findings; fixture = $fixture; passed = $false }
try {
    Invoke-Api GET '/actuator/health' | Out-Null
    $seedToken = Get-Token $AdminEmail $AdminPassword
    $adminHeaders = Headers-ConToken $seedToken
    $roles = @(Invoke-Api GET '/roles' $null $adminHeaders)
    $tag = [guid]::NewGuid().ToString('N')
    $passwordPrueba = "Prueba-$tag!"
    $fixture.tag = $tag
    $fixture.auth = @{ clienteEmail = "CLIENTE-$tag@example.com"; clientePassword = $passwordPrueba }
    foreach ($roleName in @('CLIENTE', 'ORGANIZADOR', 'ADMINISTRADOR', 'PERSONAL_ACCESO')) {
        $role = $roles | Where-Object name -eq $roleName | Select-Object -First 1
        if ($null -eq $role) { throw "No existe el rol $roleName" }
        $email = "$roleName-$tag@example.com"
        $registro = @{
            nombre = "Prueba $roleName"; email = $email
            dni = [guid]::NewGuid().ToString('N').Substring(0,20); ciudad = 'Tandil'; password = $passwordPrueba
        }
        $usuario = Invoke-Api POST '/auth/registro' $registro @{} @(201)
        if ($roleName -ne 'CLIENTE') {
            $cambioRol = @{
                nombre = $registro.nombre; email = $registro.email; dni = $registro.dni
                ciudad = $registro.ciudad; idRol = $role.id
            }
            $usuario = Invoke-Api PUT "/usuarios/$($usuario.id)" $cambioRol $adminHeaders
        }
        $fixture[$roleName] = $usuario
        $tokens[$roleName] = Get-Token $email $passwordPrueba
    }
    $clienteAjenoEmail = "CLIENTE-AJENO-$tag@example.com"
    $clienteAjenoRegistro = @{
         nombre = 'Cliente ajeno'
         email = $clienteAjenoEmail
         dni = [guid]::NewGuid().ToString('N').Substring(0,20)
         ciudad = 'Tandil'
         password = $passwordPrueba
    }
    $clienteAjeno = Invoke-Api POST '/auth/registro' $clienteAjenoRegistro @{} @(201)
    $clienteAjenoHeaders = Headers-ConToken (Get-Token $clienteAjenoEmail $passwordPrueba)

    $clienteHeaders = Headers-ConToken $tokens.CLIENTE
    $organizadorHeaders = Headers-ConToken $tokens.ORGANIZADOR
    $adminFixtureHeaders = Headers-ConToken $tokens.ADMINISTRADOR
    $personalHeaders = Headers-ConToken $tokens.PERSONAL_ACCESO
    $clientId = $fixture.CLIENTE.id
    $ownerId = $fixture.ORGANIZADOR.id
    $place = Invoke-Api POST '/api/lugares' @{ nombre = "Teatro $tag"; direccion = 'Prueba 123'; ciudad = 'Tandil'; latitud = -37.3288; longitud = -59.1367; tipo = 'TEATRO' } $organizadorHeaders @(201)
    $artist = Invoke-Api POST '/api/artistas' @{ nombre = "Artista $tag"; genero = 'Rock' } $organizadorHeaders @(201)
    $spoofOrganizadorHeaders = Headers-ConToken $tokens.ORGANIZADOR
    $spoofOrganizadorHeaders['X-Organizador-Id'] = "$clientId"
    $event = Invoke-Api POST '/api/eventos' @{
        nombre = "Integracion $tag"; descripcion = 'Fixture automatizado'; tipo = 'RECITAL'; idOrganizador = $clientId
        participantesIds = @($artist.id); fechaHora = [DateTime]::UtcNow.AddDays(10).ToString('yyyy-MM-ddTHH:mm:ss'); idLugar = $place.id; demanda = $false
    } $spoofOrganizadorHeaders @(201)
    Assert-Equal $event.idOrganizador $ownerId 'Organizador derivado del JWT'
    $fixture.eventId = $event.id
    $eventId = $event.id
    $checks.Add('Creacion de datos por Gateway con identidad verificada')
    # Un borrador no debe aparecer en el catalogo publico.
    Invoke-Api GET "/api/eventos/$eventId" $null $clienteHeaders @(404) | Out-Null
    Invoke-Api PATCH "/api/eventos/$eventId/publicar" $null (Headers-ConToken $tokens.CLIENTE) @(403) | Out-Null
    Invoke-Api PATCH "/api/eventos/$eventId/publicar" $null $organizadorHeaders @(409) | Out-Null
    $checks.Add('Borrador privado, rol incorrecto rechazado e inventario obligatorio al publicar')
    $sector = Invoke-Api POST '/sectores' @{ idLugar = $place.id; nombre = "Campo $tag"; tipo = 'CAMPO'; capacidadTotal = 10 } $organizadorHeaders @(200,201)
    $sectorId = $sector.id
    Invoke-Api PUT "/eventos/$eventId/inventario" @{limitePorCliente = 4} $organizadorHeaders @(204) | Out-Null
    Invoke-Api PUT "/eventos/$eventId/sectores" @{idSector = $sectorId; precio = 1500; capacidadHabilitada = 10} $organizadorHeaders | Out-Null
    Invoke-Api PATCH "/api/eventos/$eventId/publicar" $null $organizadorHeaders | Out-Null
    $checks.Add('SectorAsiento consulta Eventos y Eventos valida inventario real al publicar')
    $availabilityPath = "/eventos/$eventId/sectores/$sectorId/disponibilidad"
    $fixture.availabilityPath = $availabilityPath
    $before = Invoke-Api GET $availabilityPath $null $clienteHeaders
    Assert-Equal $before.disponibles 10 'Capacidad inicial'
    $order = @{claveIdempotencia = "compra-$tag"; idCliente = $clientId; idEvento = $eventId; idSector = $sectorId; cantidadEntradas = 1; medioDePago = 'TARJETA'}
    $purchase = Invoke-Api POST '/compras' $order $clienteHeaders @(201)
    $purchaseId = $purchase.id
    Assert-Equal $purchase.idCliente $clientId 'Cliente derivado del JWT'
    Assert-Equal $purchase.estadoCompra 'PENDIENTE' 'Compra pendiente'
    $again = Invoke-Api POST '/compras' $order $clienteHeaders @(200,201)
    Assert-Equal $again.id $purchaseId 'Compra idempotente'
    $paid = Invoke-Api PUT "/compras/$purchaseId/pago?aprobado=true" $null $clienteHeaders
    Assert-Equal $paid.estadoCompra 'APROBADA' 'Pago aprobado'
    $tickets = @(Invoke-Api GET "/entradas/compra/$purchaseId" $null $clienteHeaders)
    Assert-Equal $tickets.Count 1 'Una entrada emitida'
    $ticket = $tickets[0]
    $byQr = Invoke-Api GET "/entradas/qr/$([Uri]::EscapeDataString($ticket.qr))" $null $personalHeaders
    Assert-Equal $byQr.id $ticket.id 'Consulta QR por personal de acceso'
    $sold = Invoke-Api GET $availabilityPath $null $clienteHeaders
    Assert-Equal $sold.vendidos 1 'Compra confirmada en inventario real'
    Assert-Equal $sold.disponibles 9 'Descuento de disponibilidad'
    $checks.Add('Compra, reintento idempotente, pago simulado, QR e inventario entre servicios reales')

    # Usa el limite configurado por Compose para admitir a ese numero de clientes
    # y dejar al siguiente esperando, sin reducir artificialmente la configuracion.
    $limiteFila = 10
    $limiteFilaTexto = [Environment]::GetEnvironmentVariable('FILA_MAX_COMPRADORES_SIMULTANEOS')
    $archivoEnv = Join-Path $PSScriptRoot '../../.env'
    if ([string]::IsNullOrWhiteSpace($limiteFilaTexto) -and (Test-Path -LiteralPath $archivoEnv)) {
        $lineaLimite = Get-Content -LiteralPath $archivoEnv |
            Where-Object { $_ -match '^\s*FILA_MAX_COMPRADORES_SIMULTANEOS\s*=' } |
            Select-Object -Last 1
        if ($lineaLimite) {
            $limiteFilaTexto = ($lineaLimite -replace '^\s*FILA_MAX_COMPRADORES_SIMULTANEOS\s*=', '').Trim().Trim('"', "'")
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($limiteFilaTexto)) {
        $limiteParseado = 0
        if (-not [int]::TryParse($limiteFilaTexto, [ref]$limiteParseado) -or $limiteParseado -le 0) {
            throw 'FILA_MAX_COMPRADORES_SIMULTANEOS debe ser un entero positivo.'
        }
        $limiteFila = $limiteParseado
    }

    $eventoFila = Invoke-Api POST '/api/eventos' @{
        nombre = "Fila $tag"; descripcion = 'Recorrido integrado de fila y compra'; tipo = 'RECITAL'
        idOrganizador = $ownerId; participantesIds = @($artist.id)
        fechaHora = [DateTime]::UtcNow.AddDays(11).ToString('yyyy-MM-ddTHH:mm:ss')
        idLugar = $place.id; demanda = $true
    } $organizadorHeaders @(201)
    $fixture.filaEventId = $eventoFila.id
    Invoke-Api PUT "/eventos/$($eventoFila.id)/inventario" @{limitePorCliente = 1} $organizadorHeaders @(204) | Out-Null
    Invoke-Api PUT "/eventos/$($eventoFila.id)/sectores" @{
        idSector = $sectorId; precio = 1500; capacidadHabilitada = 1
    } $organizadorHeaders | Out-Null
    Invoke-Api PATCH "/api/eventos/$($eventoFila.id)/publicar" $null $organizadorHeaders | Out-Null

    $compradoresFila = [System.Collections.Generic.List[object]]::new()
    $compradoresFila.Add(@{ id = $clientId; token = $tokens.CLIENTE })
    $compradoresFila.Add(@{ id = $clienteAjeno.id; token = (Get-Token $clienteAjenoEmail $passwordPrueba) })
    while ($compradoresFila.Count -lt ($limiteFila + 1)) {
        $indice = $compradoresFila.Count
        $emailFila = "FILA-$tag-$indice@example.com"
        $registroFila = @{
            nombre = "Comprador fila $indice"; email = $emailFila
            dni = [guid]::NewGuid().ToString('N').Substring(0,20); ciudad = 'Tandil'; password = $passwordPrueba
        }
        $usuarioFila = Invoke-Api POST '/auth/registro' $registroFila @{} @(201)
        $compradoresFila.Add(@{ id = $usuarioFila.id; token = (Get-Token $emailFila $passwordPrueba) })
    }
    $fixture.filaConcurrentLimit = $limiteFila
    $fixture.filaClientIds = @($compradoresFila | ForEach-Object { $_.id })

    $turnosFila = [System.Collections.Generic.List[object]]::new()
    foreach ($comprador in $compradoresFila) {
        $headersComprador = Headers-ConToken $comprador.token
        $turno = Invoke-Api POST "/api/eventos/$($eventoFila.id)/fila" $null $headersComprador
        $turnosFila.Add(@{ comprador = $comprador; headers = $headersComprador; turno = $turno })
    }
    for ($i = 0; $i -lt $limiteFila; $i++) {
        Assert-Equal $turnosFila[$i].turno.estado 'ADMITIDO' "Turno simultaneo $($i + 1)"
    }
    Assert-Equal $turnosFila[$limiteFila].turno.estado 'EN_ESPERA' 'Siguiente comprador inicialmente en espera'

    $compradorAdmitido = $turnosFila[0]
    $compraFila = Invoke-Api POST '/compras' @{
        claveIdempotencia = "compra-fila-$tag"; idCliente = $compradorAdmitido.comprador.id
        idEvento = $eventoFila.id; idSector = $sectorId; cantidadEntradas = 1
        medioDePago = 'TARJETA'; autorizacionCompra = $compradorAdmitido.turno.autorizacionCompra
    } $compradorAdmitido.headers @(201)
    $pagoFila = Invoke-Api PUT "/compras/$($compraFila.id)/pago?aprobado=true" $null $compradorAdmitido.headers
    Assert-Equal $pagoFila.estadoCompra 'APROBADA' 'Pago de compra con turno'

    $estadoCompradorSiguiente = Invoke-Api GET "/api/eventos/$($eventoFila.id)/fila/mi-turno" $null $turnosFila[$limiteFila].headers
    Assert-Equal $estadoCompradorSiguiente.estado 'ADMITIDO' 'El siguiente entra tras pagar el primero'
    $estadoCompradorAnterior = Invoke-Api GET "/api/eventos/$($eventoFila.id)/fila/mi-turno" $null $compradorAdmitido.headers
    Assert-Equal $estadoCompradorAnterior.estado 'COMPLETADO' 'El turno pagado queda completado'
    $checks.Add('Fila, autorizacion JWT, compra real, pago y admision del siguiente con Redis y Eventos reales')

    Invoke-Api POST '/solicitudes' @{idEntrada = $ticket.id; idCliente = $clientId; descripcion = 'Cliente ajeno'} $clienteAjenoHeaders @(403) | Out-Null
    Invoke-Api POST '/compras' @{} $clienteHeaders @(400) | Out-Null
    $checks.Add('Solicitud de otro cliente rechazada y compra invalida rechazada')
    if ($AuditSecurity) {
        # Controles estrictos: cualquier respuesta distinta de 401/403 se considera un hallazgo.
        $spoofHeaders = Headers-ConToken $tokens.CLIENTE
        $spoofHeaders['X-Organizador-Id'] = "$ownerId"
        $probes = @(
            @{name = 'Lectura anonima de usuarios'; method = 'GET'; path = '/usuarios'; headers = @{}},
            @{name = 'Lectura anonima de compra ajena'; method = 'GET'; path = "/compras/$purchaseId"; headers = @{}},
            @{name = 'Token invalido aceptado'; method = 'GET'; path = "/compras/$purchaseId"; headers = @{Authorization = 'Bearer invalid'}},
            @{name = 'API interna accesible por Gateway'; method = 'GET'; path = "/api/eventos/interno/$eventId"; headers = $organizadorHeaders},
            @{name = 'Suplantacion por X-Organizador-Id'; method = 'GET'; path = '/api/eventos/mis-eventos'; headers = $spoofHeaders}
        )
        foreach ($probe in $probes) {
            $response = Invoke-WebRequest -Uri "$BaseUrl$($probe.path)" -Method $probe.method -Headers $probe.headers -SkipHttpErrorCheck -TimeoutSec 30
            $status = [int]$response.StatusCode
            $findings.Add(@{name = $probe.name; path = $probe.path; status = $status; passed = ($status -in @(401,403)); severity = 'HIGH'})
        }
        $misEventos = @(Invoke-Api GET '/api/eventos/mis-eventos' $null $spoofOrganizadorHeaders)
        $ajenos = @($misEventos | Where-Object { $_.idOrganizador -ne $ownerId })
        if ($misEventos.Count -eq 0 -or $eventId -notin @($misEventos.id) -or $ajenos.Count -gt 0) {
            throw 'La suplantacion de X-Organizador-Id devolvio eventos ajenos u oculto los propios'
        }
        $checks.Add('X-Organizador-Id reemplazado por el sub verificado del JWT')
    }
    $request = Invoke-Api POST '/solicitudes' @{idEntrada = $ticket.id; idCliente = $clientId; descripcion = 'Devolucion automatizada'} $clienteHeaders @(201)
    $resolution = @{idAdministrador = $fixture.ADMINISTRADOR.id; estadoSolicitud = 'ACEPTADA'}
    $refund = Invoke-Api PUT "/solicitudes/$($request.id)/resolver" $resolution $adminFixtureHeaders
    Assert-Equal $refund.estadoReintegro 'COMPLETADO' 'Reintegro simulado'
    Invoke-Api PUT "/solicitudes/$($request.id)/resolver" $resolution $adminFixtureHeaders | Out-Null
    $after = Invoke-Api GET $availabilityPath $null $clienteHeaders
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
