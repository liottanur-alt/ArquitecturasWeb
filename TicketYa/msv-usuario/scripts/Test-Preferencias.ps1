#requires -Version 7.0
param(
    [string]$BaseUrl = 'http://127.0.0.1:8004',
    [string]$GatewayUrl = 'http://127.0.0.1:8080',
    [string]$EventosUrl = 'http://127.0.0.1:8005',
    [switch]$RestartService,
    [switch]$TestEventosUnavailable
)
$ErrorActionPreference = 'Stop'
$usuario = $null
$artista = $null
$tag = [guid]::NewGuid().ToString('N')
$rootCompose = (Resolve-Path "$PSScriptRoot/../../docker-compose.yml").Path
function Assert-PreferenciasLocales {
    $preferencias = Invoke-RestMethod "$GatewayUrl/usuarios/$($usuario.id)/preferencias" -TimeoutSec 15
    if ($preferencias.ciudadPreferida -ne 'Mar del Plata' -or
        (($preferencias.tiposEventoPreferidos | Sort-Object) -join ',') -ne 'RECITAL,TEATRO' -or
        $preferencias.artistasFavoritos.Count -ne 1 -or $preferencias.artistasFavoritos[0] -ne $artista.id) {
        throw 'La consulta local de preferencias devolvio datos incorrectos'
    }
}
function Assert-State {
    $perfil = Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)" -TimeoutSec 15
    if ($perfil.nombre -ne 'Prueba preferencias' -or $perfil.ciudad -ne 'Tandil' -or
        $perfil.email -ne "$tag@example.com" -or $perfil.dni -ne $tag.Substring(0,20) -or
        $perfil.idRol -ne $rol.id -or $perfil.ciudadPreferida -ne 'Mar del Plata' -or
        (($perfil.tiposEventoPreferidos | Sort-Object) -join ',') -ne 'RECITAL,TEATRO') {
        throw 'El perfil o las preferencias no coinciden con lo guardado'
    }
    $favoritos = Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)/artistas-favoritos" -TimeoutSec 15
    if ($favoritos.Count -ne 1 -or $favoritos[0].idArtista -ne $artista.id) {
        throw 'Se modificaron los artistas favoritos'
    }
    Assert-PreferenciasLocales
}
try {
    $roles = Invoke-RestMethod "$BaseUrl/roles" -TimeoutSec 15
    $rol = $roles | Where-Object name -eq 'CLIENTE' | Select-Object -First 1
    $usuario = Invoke-RestMethod "$BaseUrl/usuarios" -Method Post -ContentType 'application/json' -TimeoutSec 15 -Body (@{
        nombre='Prueba preferencias'; email="$tag@example.com"; dni=$tag.Substring(0,20); ciudad='Tandil'; idRol=$rol.id
    } | ConvertTo-Json)
    $artista = Invoke-RestMethod "$EventosUrl/api/artistas" -Method Post -ContentType 'application/json' -TimeoutSec 15 -Body (@{
        nombre="Preferencias $tag"; genero='Rock'
    } | ConvertTo-Json)
    Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)/artistas-favoritos/$($artista.id)" -Method Put -TimeoutSec 15 | Out-Null
    # Guardar por Gateway y consultar directamente tambien verifica el enrutamiento.
    Invoke-RestMethod "$GatewayUrl/usuarios/$($usuario.id)/preferencias" -Method Put -ContentType 'application/json' -TimeoutSec 15 -Body '{"tiposEventoPreferidos":["RECITAL","TEATRO","RECITAL"],"ciudadPreferida":" Mar del Plata "}' | Out-Null
    Assert-State
    if ($RestartService) {
        & docker compose -f $rootCompose restart usuarios
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo reiniciar Usuarios' }
        & docker compose -f $rootCompose up -d --wait --wait-timeout 120 usuarios
        if ($LASTEXITCODE -ne 0) { throw 'Usuarios no quedo saludable tras reiniciar' }
        Assert-State
    }
    if ($TestEventosUnavailable) {
        try {
            & docker compose -f $rootCompose stop eventos
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo detener Eventos' }
            # HU2 debe devolver 200 aun cuando el catalogo real no responde.
            Assert-PreferenciasLocales
            $catalogo = Invoke-WebRequest "$BaseUrl/usuarios/$($usuario.id)/artistas-favoritos" -SkipHttpErrorCheck -TimeoutSec 20
            if ($catalogo.StatusCode -ne 503) { throw 'No se comprobo la caida real del catalogo' }
        } finally {
            & docker compose -f $rootCompose up -d --wait --wait-timeout 180 eventos
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo restaurar Eventos' }
        }
    }
    $out = Join-Path $PSScriptRoot '../.local/preferencias-result.json'
    New-Item -ItemType Directory -Force (Split-Path $out) | Out-Null
    @{passed=$true; checkedAt=[DateTimeOffset]::Now.ToString('o'); restartVerified=[bool]$RestartService;
      eventosUnavailableVerified=[bool]$TestEventosUnavailable;
      checks=@('PUT por Gateway','GET persistido','Tipos deduplicados','Ciudad independiente','Perfil conservado','Artistas reales conservados')} |
        ConvertTo-Json -Depth 4 | Set-Content $out
    Write-Output 'OK: preferencias, perfil y artistas favoritos conservados; persistencia verificada.'
} finally {
    if ($null -ne $usuario) { Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)" -Method Delete -TimeoutSec 15 | Out-Null }
    if ($null -ne $artista) { Invoke-RestMethod "$EventosUrl/api/artistas/$($artista.id)" -Method Delete -TimeoutSec 15 | Out-Null }
}
