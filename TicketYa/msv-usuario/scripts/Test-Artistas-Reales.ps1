#requires -Version 7.0
param(
    [string]$BaseUrl = 'http://127.0.0.1:8004',
    [string]$EventosUrl = 'http://127.0.0.1:8005',
    [switch]$RunPostman,
    [switch]$TestOutage
)
$ErrorActionPreference = 'Stop'
$modulePath = (Resolve-Path "$PSScriptRoot/..").Path
$rootCompose = (Resolve-Path "$modulePath/../docker-compose.yml").Path
$reportPath = Join-Path $modulePath '.local/postman-real'
New-Item -ItemType Directory -Force $reportPath | Out-Null
$artista = $null
$usuario = $null
$checks = [System.Collections.Generic.List[string]]::new()
$name = 'Artista prueba ' + [guid]::NewGuid().ToString('N')
function Assert-OtherPreferences {
    $perfil = Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)" -TimeoutSec 15
    $preferencias = Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)/preferencias" -TimeoutSec 15
    if ($perfil.nombre -ne 'Integracion artistas' -or $perfil.email -ne "$tag@example.com" -or
        $perfil.dni -ne $tag.Substring(0,20) -or $perfil.ciudad -ne 'Tandil' -or $perfil.idRol -ne $rol.id -or
        $preferencias.ciudadPreferida -ne 'Rosario' -or
        (($preferencias.tiposEventoPreferidos | Sort-Object) -join ',') -ne 'FESTIVAL,RECITAL') {
        throw 'Una operacion sobre favoritos modifico el perfil o las otras preferencias'
    }
}
try {
    $artista = Invoke-RestMethod "$EventosUrl/api/artistas" -Method Post -ContentType 'application/json' -Body (@{nombre=$name; genero='Rock'} | ConvertTo-Json) -TimeoutSec 15
    & "$PSScriptRoot/Test-Api.ps1" -BaseUrl $BaseUrl -IdArtistaPrueba $artista.id -NombreArtista $name
    $checks.Add('CRUD y favoritos con artista creado en Eventos real')

    if ($RunPostman) {
        # Los datos externos son opcionales: sin archivo, ejecutar dos recorridos con datos unicos.
        $iterationArgs = @('--iteration-count', '2')
        if (Test-Path "$modulePath/datos-prueba.postman.json") {
            $iterationArgs = @('--iteration-data', '/etc/newman/datos-prueba.postman.json')
        }
        & docker run --rm --network ticketya-db_default --mount "type=bind,source=$modulePath,target=/etc/newman,readonly" --mount "type=bind,source=$reportPath,target=/reports" postman/newman:alpine run /etc/newman/TicketYa-Usuarios.postman_collection.json @iterationArgs --env-var baseUrl=http://usuarios:8004 --env-var eventosBaseUrl=http://eventos:8005 --env-var modoArtistas=real --reporters cli,json --reporter-json-export /reports/results.json --timeout-request 10000
        if ($LASTEXITCODE -ne 0) { throw 'Fallo la coleccion Postman con Eventos real' }
        $checks.Add('Coleccion Postman completa con dos iteraciones y artista real')
    }

    $roles = Invoke-RestMethod "$BaseUrl/roles" -TimeoutSec 15
    $rol = $roles | Where-Object name -eq 'CLIENTE' | Select-Object -First 1
    $tag = [guid]::NewGuid().ToString('N')
    $usuario = Invoke-RestMethod "$BaseUrl/usuarios" -Method Post -ContentType 'application/json' -Body (@{nombre='Integracion artistas'; email="$tag@example.com"; dni=$tag.Substring(0,20); ciudad='Tandil'; idRol=$rol.id} | ConvertTo-Json) -TimeoutSec 15
    Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)/preferencias" -Method Put -ContentType 'application/json' -TimeoutSec 15 -Body '{"tiposEventoPreferidos":["RECITAL","FESTIVAL"],"ciudadPreferida":"Rosario"}' | Out-Null
    $favoritosUrl = "$BaseUrl/usuarios/$($usuario.id)/artistas-favoritos"
    1..2 | ForEach-Object {
        Invoke-RestMethod "$favoritosUrl/$($artista.id)" -Method Put -TimeoutSec 15 | Out-Null
        Assert-OtherPreferences
    }
    Invoke-RestMethod "$favoritosUrl/9223372036854775806" -Method Delete -TimeoutSec 15 | Out-Null
    Assert-OtherPreferences
    $ids = (Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)/preferencias" -TimeoutSec 15).artistasFavoritos
    if ($ids.Count -ne 1 -or $ids[0] -ne $artista.id) { throw 'Alta duplicada o baja ausente altero los favoritos' }
    1..2 | ForEach-Object {
        Invoke-RestMethod "$favoritosUrl/$($artista.id)" -Method Delete -TimeoutSec 15 | Out-Null
        Assert-OtherPreferences
    }
    $ids = (Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)/preferencias" -TimeoutSec 15).artistasFavoritos
    if ($ids.Count -ne 0) { throw 'La baja del favorito no quedo persistida' }
    Invoke-RestMethod "$favoritosUrl/$($artista.id)" -Method Put -TimeoutSec 15 | Out-Null
    Assert-OtherPreferences
    $checks.Add('HU3: altas y bajas idempotentes conservan perfil, tipos y ciudad preferida')

    if ($TestOutage) {
        # Solo para el Compose local: se restaura Eventos aunque falle la comprobacion.
        try {
            & docker compose -f $rootCompose stop eventos
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo detener Eventos' }
            $response = Invoke-WebRequest $favoritosUrl -SkipHttpErrorCheck -TimeoutSec 20
            if ($response.StatusCode -ne 503) { throw 'Eventos caido no devolvio 503' }
            Assert-OtherPreferences
            $ids = (Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)/preferencias" -TimeoutSec 15).artistasFavoritos
            if ($ids.Count -ne 1 -or $ids[0] -ne $artista.id) { throw 'El fallo del catalogo borro favoritos' }
        } finally {
            & docker compose -f $rootCompose up -d --wait --wait-timeout 180 eventos
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo restaurar Eventos' }
        }
        # Tras reiniciar un contenedor, la cache DNS/conexiones puede tardar en renovarse.
        # Comprobar hasta 15 veces, aceptando solo 503 durante la recuperacion.
        $recovered = $false
        for ($attempt = 0; $attempt -lt 15; $attempt++) {
            $response = Invoke-WebRequest $favoritosUrl -SkipHttpErrorCheck -TimeoutSec 15
            if ($response.StatusCode -eq 200) {
                $favorites = $response.Content | ConvertFrom-Json
                $recovered = $true
                break
            }
            if ($response.StatusCode -ne 503) { throw "Recuperacion devolvio $($response.StatusCode)" }
            Start-Sleep -Seconds 2
        }
        if (-not $recovered) { throw 'Usuarios no recupero la conexion con Eventos' }
        if ($favorites[0].idArtista -ne $artista.id) { throw 'Se perdio el favorito durante la caida' }
        $checks.Add('Caida real de Eventos: 503 y recuperacion sin perder favoritos')
    }

    Invoke-RestMethod "$EventosUrl/api/artistas/$($artista.id)" -Method Put -ContentType 'application/json' -Body (@{nombre="$name actualizado"; genero='Jazz'} | ConvertTo-Json) -TimeoutSec 15 | Out-Null
    $favorites = Invoke-RestMethod $favoritosUrl -TimeoutSec 15
    if ($favorites[0].nombre -ne "$name actualizado" -or $favorites[0].genero -ne 'Jazz') { throw 'Usuarios no refleja los cambios del catalogo real' }
    $checks.Add('Cambios de nombre y genero en Eventos visibles desde Usuarios')

    Invoke-RestMethod "$EventosUrl/api/artistas/$($artista.id)" -Method Delete -TimeoutSec 15 | Out-Null
    $artista = $null
    $response = Invoke-WebRequest $favoritosUrl -SkipHttpErrorCheck -TimeoutSec 15
    if ($response.StatusCode -ne 502) { throw 'Artista eliminado no devolvio 502' }
    Assert-OtherPreferences
    $checks.Add('Artista eliminado en Eventos: Usuarios devuelve 502')
    @{passed=$true; checkedAt=[DateTimeOffset]::Now.ToString('o'); checks=$checks} | ConvertTo-Json -Depth 5 | Set-Content "$reportPath/integration.json"
    Write-Output 'OK: integracion con artistas reales verificada.'
} finally {
    if ($null -ne $usuario) { Invoke-RestMethod "$BaseUrl/usuarios/$($usuario.id)" -Method Delete -TimeoutSec 15 | Out-Null }
    if ($null -ne $artista) { Invoke-RestMethod "$EventosUrl/api/artistas/$($artista.id)" -Method Delete -TimeoutSec 15 | Out-Null }
}
