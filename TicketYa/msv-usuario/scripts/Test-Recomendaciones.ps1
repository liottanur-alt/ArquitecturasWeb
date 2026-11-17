#requires -Version 7.0
param(
    [string]$BaseUrl = 'http://127.0.0.1:8004',
    [string]$GatewayUrl = 'http://127.0.0.1:8080',
    [string]$EventosUrl = 'http://127.0.0.1:8005',
    [switch]$TestOutage
)
$ErrorActionPreference = 'Stop'
$usuario = $null
$rootCompose = (Resolve-Path "$PSScriptRoot/../../docker-compose.yml").Path
$tag = [guid]::NewGuid().ToString('N')
function Normalizar-Ciudad([string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) { return '' }
    return (($value.Normalize([Text.NormalizationForm]::FormD) -replace '\p{M}', '').Trim() -replace '\s+', ' ').ToLowerInvariant()
}
try {
    # Lee eventos existentes: no cambia el catalogo ni crea inventario de prueba.
    $catalogo = Invoke-RestMethod "$EventosUrl/api/eventos" -TimeoutSec 15
    $ahora = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow, 'America/Argentina/Buenos_Aires').DateTime
    $elegibles = @($catalogo | Where-Object {
        $_.estado -eq 'EN_CURSO' -and $_.estadoPublicacion -eq 'PUBLICADO' -and
        [DateTime]$_.fechaHora -gt $ahora.AddMinutes(1) -and
        -not [string]::IsNullOrWhiteSpace($_.lugar.ciudad) -and $_.participantes.Count -gt 0
    })
    if ($elegibles.Count -eq 0) { throw 'Se necesita al menos un evento futuro publicado con ciudad y artista para esta prueba real' }
    $seleccionado = $elegibles[0]
    $favorito = [long]$seleccionado.participantes[0].id
    $roles = Invoke-RestMethod "$BaseUrl/roles" -TimeoutSec 15
    $rol = $roles | Where-Object name -eq 'CLIENTE' | Select-Object -First 1
    $usuario = Invoke-RestMethod "$BaseUrl/usuarios" -Method Post -ContentType 'application/json' -TimeoutSec 15 -Body (@{
        nombre='Prueba HU4'; email="$tag@example.com"; dni=$tag.Substring(0,20); ciudad='Ciudad del perfil'; idRol=$rol.id
    } | ConvertTo-Json)
    $uri = "$GatewayUrl/usuarios/$($usuario.id)/recomendaciones/por-preferencias"
    $vacio = Invoke-RestMethod $uri -TimeoutSec 15
    if (@($vacio).Count -ne 0) { throw 'Sin preferencias no debe recomendar eventos genericos' }
    Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)/preferencias" -Method Put -ContentType 'application/json' -TimeoutSec 15 -Body (@{
        tiposEventoPreferidos=@($seleccionado.tipo); ciudadPreferida=$seleccionado.lugar.ciudad.ToUpperInvariant()
    } | ConvertTo-Json) | Out-Null
    Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)/artistas-favoritos/$favorito" -Method Put -TimeoutSec 15 | Out-Null
    $antes = Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)/preferencias" -TimeoutSec 15 | ConvertTo-Json -Depth 6 -Compress
    $recomendaciones = Invoke-RestMethod $uri -TimeoutSec 15
    $ids = @($recomendaciones | ForEach-Object idEvento)
    if (@($ids | Select-Object -Unique).Count -ne $ids.Count) { throw 'Eventos recomendados duplicados' }
    $indice = @{}
    foreach ($evento in $catalogo) { $indice[[long]$evento.id] = $evento }
    $previo = $null
    foreach ($r in $recomendaciones) {
        $e = $indice[[long]$r.idEvento]
        if ($null -eq $e -or $e.estado -ne 'EN_CURSO' -or $e.estadoPublicacion -ne 'PUBLICADO' -or [DateTime]$e.fechaHora -le $ahora) {
            throw 'Se recomendo un evento pasado o no disponible para la venta'
        }
        $puntos = 0
        $motivos = @()
        if ($favorito -in @($e.participantes.id)) { $puntos += 5; $motivos += 'Participa un artista favorito' }
        if ($e.tipo -eq $seleccionado.tipo) { $puntos += 3; $motivos += 'Coincide con un tipo de evento preferido' }
        if ((Normalizar-Ciudad $e.lugar.ciudad) -eq (Normalizar-Ciudad $seleccionado.lugar.ciudad)) { $puntos += 2; $motivos += 'Se realiza en tu ciudad de preferencia' }
        if ($puntos -le 0 -or $r.puntuacion -ne $puntos -or $r.nombre -ne $e.nombre -or $r.tipo -ne $e.tipo -or
            [DateTime]$r.fechaHora -ne [DateTime]$e.fechaHora -or $r.ciudad -ne $e.lugar.ciudad -or
            (($r.motivos -join '|') -ne ($motivos -join '|'))) { throw 'Puntuacion, motivos o datos no coinciden con el catalogo real' }
        if ($null -ne $previo -and ($previo.puntuacion -lt $r.puntuacion -or
            ($previo.puntuacion -eq $r.puntuacion -and ([DateTime]$previo.fechaHora -gt [DateTime]$r.fechaHora -or
            ([DateTime]$previo.fechaHora -eq [DateTime]$r.fechaHora -and $previo.idEvento -gt $r.idEvento))))) { throw 'Orden de recomendaciones incorrecto' }
        $previo = $r
    }
    $coincidencia = $recomendaciones | Where-Object idEvento -eq $seleccionado.id | Select-Object -First 1
    if ($null -eq $coincidencia -or $coincidencia.puntuacion -ne 10) { throw 'No se recomendo la coincidencia de los tres criterios' }
    $despues = Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)/preferencias" -TimeoutSec 15 | ConvertTo-Json -Depth 6 -Compress
    if ($antes -ne $despues) { throw 'Consultar recomendaciones modifico las preferencias' }

    if ($TestOutage) {
        try {
            & docker compose -f $rootCompose stop eventos
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo detener Eventos' }
            $respuesta = Invoke-WebRequest $uri -SkipHttpErrorCheck -TimeoutSec 20
            if ($respuesta.StatusCode -ne 503) { throw 'Eventos caido no devolvio 503 en recomendaciones' }
            $persistido = Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)/preferencias" -TimeoutSec 15 | ConvertTo-Json -Depth 6 -Compress
            if ($persistido -ne $antes) { throw 'La caida de Eventos altero las preferencias' }
        } finally {
            & docker compose -f $rootCompose up -d --wait --wait-timeout 180 eventos
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo restaurar Eventos' }
        }
        $recuperado = $false
        for ($intento = 0; $intento -lt 15; $intento++) {
            $respuesta = Invoke-WebRequest $uri -SkipHttpErrorCheck -TimeoutSec 15
            if ($respuesta.StatusCode -eq 200) { $recuperado = $true; break }
            if ($respuesta.StatusCode -ne 503) { throw 'Error inesperado al recuperar Eventos' }
            Start-Sleep -Seconds 2
        }
        if (-not $recuperado) { throw 'Las recomendaciones no se recuperaron tras restaurar Eventos' }
    }

    # Preferencias validas sin coincidencias: una ciudad unica, sin tipos ni artistas.
    Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)/artistas-favoritos/$favorito" -Method Delete -TimeoutSec 15 | Out-Null
    Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)/preferencias" -Method Put -ContentType 'application/json' -TimeoutSec 15 -Body (@{
        tiposEventoPreferidos=@(); ciudadPreferida="Sin coincidencias $tag"
    } | ConvertTo-Json) | Out-Null
    $sinCoincidencias = Invoke-RestMethod $uri -TimeoutSec 15
    if (@($sinCoincidencias).Count -ne 0) { throw 'Sin coincidencias debe devolver []' }
    $noExiste = Invoke-WebRequest "$GatewayUrl/usuarios/9223372036854775807/recomendaciones/por-preferencias" -SkipHttpErrorCheck -TimeoutSec 15
    if ($noExiste.StatusCode -ne 404) { throw 'Usuario inexistente no devolvio 404' }
    $path = Join-Path $PSScriptRoot '../.local/recomendaciones-result.json'
    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
    @{passed=$true; checkedAt=[DateTimeOffset]::Now.ToString('o'); eventoReferencia=$seleccionado.id;
      recomendacionesVerificadas=$ids.Count; outageVerified=[bool]$TestOutage;
      checks=@('Tres criterios: 10 puntos','Datos y motivos del catalogo real','Orden por puntuacion, fecha e ID',
        'Sin duplicados','Sin eventos no vendibles','Sin coincidencias: []','Usuario inexistente: 404','Preferencias conservadas')} |
        ConvertTo-Json -Depth 5 | Set-Content $path
    Write-Output 'OK: recomendaciones verificadas contra Eventos real por Gateway.'
} finally {
    if ($null -ne $usuario) { Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)" -Method Delete -TimeoutSec 15 | Out-Null }
}
