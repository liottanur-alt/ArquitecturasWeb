#requires -Version 5.1
[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$AdminEmail = 'admin@admin.com',
    [string]$AdminPassword = $null
)

. "$PSScriptRoot/../tests/system/Common.ps1"

function Get-CompradorDemo {
    $email = 'catalogo@ticketya.invalid'
    # Deriva una contraseña propia para la cuenta de inventario sin guardarla en disco.
    $derivador = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($AdminPassword))
    try {
        $password = [BitConverter]::ToString($derivador.ComputeHash([Text.Encoding]::UTF8.GetBytes($email))).Replace('-', '')
    } finally {
        $derivador.Dispose()
    }

    $usuario = $usuarios |
        Where-Object email -eq $email | Select-Object -First 1
    if ($null -eq $usuario) {
        $usuario = Invoke-Api POST '/auth/registro' @{
            nombre = 'Compras del catálogo de demostración'
            email = $email
            dni = [Guid]::NewGuid().ToString('N').Substring(0, 20)
            ciudad = 'Buenos Aires'
            password = $password
        } @{} @(201)
    }

    return @{
        id = $usuario.id
        headers = Headers-ConToken (Get-Token $email $password)
    }
}

function Confirmar-Preventa($Evento, $Sector, $Datos, $Comprador) {
    $prefijo = "seed-catalogo-v1-$($Evento.id)-"
    $previas = @(Invoke-Api GET "/compras/cliente/$($Comprador.id)" $null $Comprador.headers |
        Where-Object claveIdempotencia -Like "$prefijo*")
    $aprobada = $previas | Where-Object estadoCompra -eq 'APROBADA' | Select-Object -First 1
    if ($aprobada) { return $aprobada }

    $compra = $previas | Where-Object estadoCompra -eq 'PENDIENTE' | Select-Object -First 1
    if ($null -eq $compra) {
        $pedido = @{
            claveIdempotencia = "$prefijo$($previas.Count + 1)"
            idCliente = $Comprador.id
            idEvento = $Evento.id
            idSector = $Sector.id
            medioDePago = 'TARJETA'
        }
        if ($Sector.tipo -eq 'NUMERADO') {
            $stock = Invoke-Api GET "/eventos/$($Evento.id)/sectores/$($Sector.id)/disponibilidad" $null $Comprador.headers
            $ids = @($stock.asientos | Where-Object estado -eq 'DISPONIBLE' |
                Select-Object -First $Datos.preventa -ExpandProperty idAsiento)
            if ($ids.Count -ne $Datos.preventa) { throw 'No quedan suficientes asientos para la preventa de demostración.' }
            $pedido.idsAsientos = $ids
        } else {
            $pedido.cantidadEntradas = $Datos.preventa
        }
        $compra = Invoke-Api POST '/compras' $pedido $Comprador.headers @(201)
    }

    $pagada = Invoke-Api PUT "/compras/$($compra.id)/pago?aprobado=true" $null $Comprador.headers
    if ($pagada.estadoCompra -ne 'APROBADA') {
        throw "La preventa de '$($Evento.nombre)' quedó $($pagada.estadoCompra). Volvé a ejecutar el seed para retomarla."
    }
    return $pagada
}

function Completar-CasosDeEntradas($Evento, $Compra, $Comprador) {
    $entradas = @(Invoke-Api GET "/entradas/compra/$($Compra.id)" $null $Comprador.headers | Sort-Object id)
    if ($entradas.Count -lt 5) { throw 'El evento de demostración requiere cinco entradas emitidas.' }
    $destinos = @('PENDIENTE', 'ACEPTADA', 'RECHAZADA')
    for ($i = 0; $i -lt $destinos.Count; $i++) {
        $entrada = $entradas[$i]
        $solicitud = Invoke-Api GET "/solicitudes/entrada/$($entrada.id)" $null $Comprador.headers | Select-Object -First 1
        if ($null -eq $solicitud) {
            $solicitud = Invoke-Api POST '/solicitudes' @{
                idEntrada = $entrada.id; idCliente = $Comprador.id
                descripcion = 'Cambio de planes. Solicitud simulada para la presentación de TicketYa.'
            } $Comprador.headers @(201)
        }
        if ($destinos[$i] -ne 'PENDIENTE' -and $solicitud.estadoSolicitud -eq 'PENDIENTE') {
            Invoke-Api PUT "/solicitudes/$($solicitud.id)/resolver" @{
                idAdministrador = $administrador.id; estadoSolicitud = $destinos[$i]
            } $adminHeaders | Out-Null
        }
    }
    $usada = $entradas[3]
    if ($usada.estadoEntrada -eq 'PAGA' -and $usada.estadoQr -eq 'NO_ESCANEADO') {
        Invoke-Api PATCH "/entradas/$($usada.id)/uso?idEvento=$($Evento.id)" $null $adminHeaders | Out-Null
    }
}

$BaseUrl = $BaseUrl.TrimEnd('/')
if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    $AdminPassword = Get-ValorEntorno 'ADMIN_SEED_PASSWORD'
}
$fixture = Import-PowerShellDataFile -LiteralPath "$PSScriptRoot/CatalogoDemo.psd1"
$adminHeaders = Headers-ConToken (Get-Token $AdminEmail $AdminPassword)
$usuarios = @(Invoke-Api GET '/usuarios' $null $adminHeaders)
$administrador = $usuarios | Where-Object email -eq $AdminEmail | Select-Object -First 1
if ($null -eq $administrador) { throw 'No se pudo identificar la cuenta administradora del seed.' }
$misEventos = @(Invoke-Api GET '/api/eventos/mis-eventos' $null $adminHeaders)
$lugares = @(Invoke-Api GET '/api/lugares' $null $adminHeaders)
$artistas = @(Invoke-Api GET '/api/artistas' $null $adminHeaders)
$comprador = Get-CompradorDemo

$hoy = [DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(-3)).Date
$ahora = [DateTimeOffset]::UtcNow.ToOffset([TimeSpan]::FromHours(-3)).DateTime
$lunes = $hoy.AddDays(-(([int]$hoy.DayOfWeek + 6) % 7))

$resultados = foreach ($datos in $fixture.Eventos) {
    $numerado = $datos.ContainsKey('filas')
    $destino = $datos['destino']
    $imagen = "/eventos-destacados/$($datos.tipo.ToLowerInvariant()).png"
    $evento = $misEventos | Where-Object {
        $propiedadImagen = $_.PSObject.Properties['imagenUrl']
        $_.nombre -eq $datos.nombre -and $null -ne $propiedadImagen -and $propiedadImagen.Value -eq $imagen
    } |
        Select-Object -First 1
    if ($null -eq $evento) {
        $datosLugar = $fixture.Lugares[$datos.lugar]
        $lugar = $lugares | Where-Object { $_.nombre -eq $datosLugar.nombre -and $_.ciudad -eq $datosLugar.ciudad } |
            Select-Object -First 1
        if ($null -eq $lugar) {
            $lugar = Invoke-Api POST '/api/lugares' $datosLugar $adminHeaders @(201)
            $lugares += $lugar
        }

        $artista = $artistas | Where-Object nombre -eq $datos.participante | Select-Object -First 1
        if ($null -eq $artista) {
            $artista = Invoke-Api POST '/api/artistas' @{
                nombre = $datos.participante
                genero = $datos.genero
            } $adminHeaders @(201)
        }

        $diaDeSemana = (([int][DayOfWeek]$datos.dia + 6) % 7)
        $fecha = $lunes.AddDays($datos.semanas * 7 + $diaDeSemana).AddHours($datos.hora)
        if ($datos['hoy']) {
            $fecha = $hoy.AddHours(23).AddMinutes(59)
            if ($fecha -le $ahora) { $fecha = $fecha.AddDays(1) }
        } elseif ($fecha -le $ahora) {
            $fecha = $fecha.AddDays(7)
        }
        $evento = Invoke-Api POST '/api/eventos' @{
            nombre = $datos.nombre
            descripcion = $datos.descripcion
            tipo = $datos.tipo
            participantesIds = @($artista.id)
            fechaHora = $fecha.ToString('yyyy-MM-ddTHH:mm:ss')
            idLugar = $lugar.id
            imagenUrl = $imagen
            demanda = [bool]$datos['demanda']
        } $adminHeaders @(201)
        $propiedadImagen = $evento.PSObject.Properties['imagenUrl']
        if ($null -eq $propiedadImagen -or $propiedadImagen.Value -ne $imagen) {
            throw 'El backend no devolvió imagenUrl. Reconstruí el servicio Eventos con esta versión del repositorio.'
        }
    }

    $tipoSector = if ($numerado) { 'NUMERADO' } else { 'CAMPO' }
    $nombreSector = if ($numerado) { "Platea · $($datos.nombre)" } else { "Acceso general · $($datos.nombre)" }
    $sector = Invoke-Api GET "/sectores?idLugar=$($evento.lugar.id)" $null $adminHeaders |
        Where-Object nombre -eq $nombreSector | Select-Object -First 1

    if ($evento.estadoPublicacion -eq 'BORRADOR') {
        if ($null -eq $sector) {
            $sector = Invoke-Api POST '/sectores' @{
                idLugar = $evento.lugar.id
                nombre = $nombreSector
                tipo = $tipoSector
                capacidadTotal = $datos.capacidad
            } $adminHeaders @(201)
        }
        if ($numerado) {
            $existentes = @(Invoke-Api GET "/asientos?idSector=$($sector.id)" $null $adminHeaders)
            foreach ($fila in $datos.filas) {
                for ($columna = 1; $columna -le $datos.columnas; $columna++) {
                    if (-not ($existentes | Where-Object { $_.fila -eq $fila -and $_.columna -eq [string]$columna })) {
                        Invoke-Api POST '/asientos' @{ idSector = $sector.id; fila = $fila; columna = [string]$columna } $adminHeaders @(201) | Out-Null
                    }
                }
            }
        }
        Invoke-Api PUT "/eventos/$($evento.id)/inventario" @{ limitePorCliente = 10 } $adminHeaders @(204)
        Invoke-Api PUT "/eventos/$($evento.id)/sectores" @{
            idSector = $sector.id
            precio = $datos.precio
            capacidadHabilitada = $datos.capacidad
        } $adminHeaders | Out-Null
        if ($destino -ne 'BORRADOR') {
            $evento = Invoke-Api PATCH "/api/eventos/$($evento.id)/publicar" $null $adminHeaders
        }
    }

    if ($evento.estadoPublicacion -eq 'PUBLICADO' -and $evento.estado -ne 'CANCELADO' -and $datos.preventa -gt 0) {
        $compra = Confirmar-Preventa $evento $sector $datos $comprador
        if ($datos['casosEntradas']) { Completar-CasosDeEntradas $evento $compra $comprador }
    }

    if ($destino -eq 'CANCELADO' -and $evento.estado -ne 'CANCELADO') {
        Invoke-Api POST "/compras/eventos/$($evento.id)/cancelacion" $null $adminHeaders | Out-Null
        $evento.estado = 'CANCELADO'
    }
    if ($destino -eq 'SUSPENDIDO' -and $evento.estadoPublicacion -eq 'PUBLICADO') {
        $evento = Invoke-Api PATCH "/api/eventos/$($evento.id)/suspender" $null $adminHeaders
    }
    $disponibilidad = if ($evento.estadoPublicacion -eq 'PUBLICADO' -and $evento.estado -ne 'CANCELADO') {
        Invoke-Api GET "/eventos/$($evento.id)/sectores/$($sector.id)/disponibilidad" $null $adminHeaders
    }
    [pscustomobject]@{
        Id = $evento.id
        Evento = $evento.nombre
        Sector = $tipoSector
        Estado = if ($evento.estadoPublicacion -eq 'PUBLICADO') { $evento.estado } else { $evento.estadoPublicacion }
        PrecioARS = if ($disponibilidad) { $disponibilidad.precio } else { $datos.precio }
        Disponibles = if ($disponibilidad) { $disponibilidad.disponibles }
    }
}

$eventosActuales = @{}
Invoke-Api GET '/api/eventos/mis-eventos' $null $adminHeaders | ForEach-Object { $eventosActuales[$_.id] = $_ }
foreach ($resultado in $resultados) {
    $actual = $eventosActuales[$resultado.Id]
    if ($actual) {
        $resultado.Estado = if ($actual.estado -eq 'CANCELADO' -or $actual.estadoPublicacion -eq 'PUBLICADO') {
            $actual.estado
        } else {
            $actual.estadoPublicacion
        }
    }
}
$resultados | Format-Table -AutoSize

