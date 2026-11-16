param(
    [string]$BaseUrl = 'http://localhost:8004',
    [long]$IdArtistaPrueba = 900001,
    [string]$NombreArtista = 'Soda Stereo'
)
$ErrorActionPreference = 'Stop'

function Assert-True($Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

# Crea solo un usuario propio, con datos unicos, y lo elimina al terminar.
$runId = [guid]::NewGuid().ToString('N')
$userId = $null
try {
    $roles = Invoke-RestMethod "$BaseUrl/roles"
    $rol = $roles | Where-Object name -eq 'CLIENTE' | Select-Object -First 1
    Assert-True ($null -ne $rol) 'No se inicializo el rol CLIENTE'
    $body = @{ nombre='Prueba Docker'; email="docker-$runId@example.com";
        dni=$runId.Substring(0,20); ciudad='Tandil'; idRol=$rol.id }
    $created = Invoke-WebRequest "$BaseUrl/usuarios" -Method Post -ContentType 'application/json' -Body ($body | ConvertTo-Json) -UseBasicParsing
    $userId = ($created.Content | ConvertFrom-Json).id
    Assert-True ($created.StatusCode -eq 201 -and $userId -gt 0) 'Fallo crear usuario'
    $uri = "$BaseUrl/usuarios/$userId"
    $body.nombre = 'Perfil actualizado'
    $updated = Invoke-RestMethod $uri -Method Put -ContentType 'application/json' -Body ($body | ConvertTo-Json)
    Assert-True ($updated.nombre -eq $body.nombre) 'Fallo actualizar usuario'

    1..2 | ForEach-Object { Invoke-RestMethod "$uri/artistas-favoritos/$IdArtistaPrueba" -Method Put | Out-Null }
    $favorites = Invoke-RestMethod "$uri/artistas-favoritos"
    Assert-True ($favorites.Count -eq 1 -and $favorites[0].nombre -eq $NombreArtista -and $favorites[0].idArtista -eq $IdArtistaPrueba) 'Favorito duplicado o respuesta de artista incorrecta'

    # Una actualizacion del perfil debe conservar los favoritos embebidos.
    Invoke-RestMethod $uri -Method Put -ContentType 'application/json' -Body ($body | ConvertTo-Json) | Out-Null
    $favorites = Invoke-RestMethod "$uri/artistas-favoritos"
    Assert-True (@($favorites).Count -eq 1) 'Actualizar perfil borro favoritos'

    $duplicateStatus = 0
    try { Invoke-RestMethod "$BaseUrl/usuarios" -Method Post -ContentType 'application/json' -Body ($body | ConvertTo-Json) | Out-Null }
    catch { if ($null -eq $_.Exception.Response) { throw }; $duplicateStatus = [int]$_.Exception.Response.StatusCode }
    Assert-True ($duplicateStatus -eq 409) 'Los datos duplicados no devolvieron 409'

    $roleStatus = 0
    try { Invoke-RestMethod "$BaseUrl/roles/$($rol.id)" -Method Delete | Out-Null }
    catch { if ($null -eq $_.Exception.Response) { throw }; $roleStatus = [int]$_.Exception.Response.StatusCode }
    Assert-True ($roleStatus -eq 409) 'Eliminar un rol en uso no devolvio 409'

    Invoke-RestMethod "$uri/artistas-favoritos/$IdArtistaPrueba" -Method Delete | Out-Null
    $favorites = Invoke-RestMethod "$uri/artistas-favoritos"
    Assert-True (@($favorites).Count -eq 0) 'No se quito el favorito'
    Invoke-RestMethod "$uri/artistas-favoritos/$IdArtistaPrueba" -Method Put | Out-Null
    Invoke-RestMethod $uri -Method Delete | Out-Null
    $userId = $null
    $deletedStatus = 0
    try { Invoke-RestMethod $uri | Out-Null }
    catch { if ($null -eq $_.Exception.Response) { throw }; $deletedStatus = [int]$_.Exception.Response.StatusCode }
    Assert-True ($deletedStatus -eq 404) 'El usuario eliminado sigue disponible'
    Write-Output 'OK: roles, CRUD, favoritos sin duplicados, conservacion de favoritos, conflictos 409 y eliminacion.'
} finally {
    if ($null -ne $userId) {
        Invoke-RestMethod "$BaseUrl/usuarios/$userId" -Method Delete | Out-Null
    }
}
