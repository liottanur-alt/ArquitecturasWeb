#requires -Version 7.0
param(
    [string]$BaseUrl = 'http://127.0.0.1:8004',
    [string]$GatewayUrl = 'http://127.0.0.1:8080',
    [string]$ComprasUrl = 'http://127.0.0.1:8003',
    [string]$SectoresUrl = 'http://127.0.0.1:8002',
    [switch]$TestOutage,
    [switch]$TestUnificadas,
    [switch]$TestInventario
)
$ErrorActionPreference = 'Stop'
$clientes = [Collections.Generic.List[object]]::new()
$operaciones = [Collections.Generic.List[object]]::new()
$reservasInventario = [Collections.Generic.List[object]]::new()
$TestUnificadas = $TestUnificadas -or $TestInventario
$inventarios = @()
$tag = [guid]::NewGuid().ToString('N')
$rootCompose = (Resolve-Path "$PSScriptRoot/../../docker-compose.yml").Path
$reporte = @{passed=$false; checks=[Collections.Generic.List[string]]::new(); outageVerified=$false}
$out = Join-Path $PSScriptRoot '../.local/compras-en-comun-result.json'
New-Item -ItemType Directory -Force (Split-Path $out) | Out-Null
function Api([string]$Metodo, [string]$Ruta, $Body = $null) {
    $argsHttp = @{Uri="$GatewayUrl$Ruta"; Method=$Metodo; TimeoutSec=25}
    if ($null -ne $Body) { $argsHttp.ContentType='application/json'; $argsHttp.Body=$Body | ConvertTo-Json -Depth 8 }
    Invoke-RestMethod @argsHttp
}
function Cliente([string]$Nombre, [string]$Tipo='CLIENTE') {
    $rol = $roles | Where-Object name -eq $Tipo | Select-Object -First 1
    $c = Api POST '/usuarios' @{nombre="HU5 $Nombre"; email="$Nombre-$tag@example.com";
        dni=[guid]::NewGuid().ToString('N').Substring(0,20); ciudad='Tandil'; idRol=$rol.id}
    $clientes.Add($c)
    return $c
}
function Comprar($Cliente, $Inventario, [int]$Cantidad=1, [bool]$Pagar=$true) {
    $c = Api POST '/compras' @{idCliente=$Cliente.id; idEvento=$Inventario.idEvento; idSector=$Inventario.idSector;
        cantidadEntradas=$Cantidad; idsAsientos=@(); medioDePago='SIMULADO'; claveIdempotencia="hu5-$([guid]::NewGuid())"}
    $operaciones.Add($c)
    if ($Pagar) {
        $c = Api PUT "/compras/$($c.id)/pago?aprobado=true"
        if ($c.estadoCompra -ne 'APROBADA') { throw 'La compra de prueba no quedo confirmada' }
    }
    return $c
}
function Reintegrar($Entrada) {
    if ($Entrada.estadoEntrada -in @('DEVOLUCION_ACEPTADA','EVENTO_CANCELADO')) { return }
    if ($Entrada.estadoEntrada -eq 'SOLICITUD_DEVOLUCION') {
        $solicitudes = Api GET "/solicitudes/entrada/$($Entrada.id)"
        $s = $solicitudes | Where-Object estadoSolicitud -eq 'PENDIENTE' | Select-Object -First 1
    } else {
        $s = Api POST '/solicitudes' @{idEntrada=$Entrada.id; idCliente=$Entrada.idCliente; descripcion="Prueba HU5 $tag"}
    }
    if ($null -eq $s) { throw 'No se encontro la solicitud propia a reintegrar' }
    $resultado = Api PUT "/solicitudes/$($s.id)/resolver" @{idAdministrador=$admin.id; estadoSolicitud='ACEPTADA'}
    if ($resultado.estadoReintegro -ne 'COMPLETADO') { throw 'El reintegro simulado no se completo' }
}
function Internas {
    Invoke-RestMethod "$ComprasUrl/interno/recomendaciones/clientes/$($yo.id)" -TimeoutSec 20
}
function CantidadY([long]$Esperado) {
    $rows = Internas
    $fila = @($rows | Where-Object idEvento -eq $y.idEvento)
    $cantidad = if ($fila.Count -eq 0) { 0L } else { [long]$fila[0].compradoresEnComun }
    if ($fila.Count -gt 1 -or $cantidad -ne $Esperado) { throw "Compradores distintos de Y: esperado $Esperado, recibido $cantidad" }
    $publicas = Api GET "/usuarios/$($yo.id)/recomendaciones/por-compras"
    $filaPublica = @($publicas | Where-Object idEvento -eq $y.idEvento)
    if (($Esperado -eq 0 -and $filaPublica.Count -ne 0) -or
        ($Esperado -gt 0 -and ($filaPublica.Count -ne 1 -or $filaPublica[0].compradoresEnComun -ne $Esperado))) {
        throw 'Usuarios no refleja la agregacion MySQL validada con Eventos'
    }
    foreach ($r in $rows) {
        if (@(Compare-Object @('idEvento','compradoresEnComun','idEventoReferencia') @($r.PSObject.Properties.Name)).Count -ne 0) { throw 'El contrato interno expone campos no agregados' }
    }
    $previo = $null
    foreach ($r in $publicas) {
        if (@(Compare-Object @('idEvento','nombre','tipo','fechaHora','ciudad','compradoresEnComun','motivo') @($r.PSObject.Properties.Name)).Count -ne 0) { throw 'La respuesta publica expone campos inesperados' }
        if ($r.idEvento -in @($x.idEvento,$z.idEvento)) { throw 'Se recomendo un evento del historial propio' }
        if ($previo -and ($previo.compradoresEnComun -lt $r.compradoresEnComun -or
            ($previo.compradoresEnComun -eq $r.compradoresEnComun -and ([DateTime]$previo.fechaHora -gt [DateTime]$r.fechaHora -or
            ([DateTime]$previo.fechaHora -eq [DateTime]$r.fechaHora -and $previo.idEvento -gt $r.idEvento))))) { throw 'Orden colaborativo incorrecto' }
        $previo = $r
    }
}
function PreferenciasY([bool]$Activar) {
    $evento = $catalogo | Where-Object id -eq $y.idEvento | Select-Object -First 1
    $tipos = if ($Activar) { @($evento.tipo) } else { @() }
    $ciudad = if ($Activar) { $evento.lugar.ciudad } else { '' }
    Api PUT "/usuarios/$($yo.id)/preferencias" @{tiposEventoPreferidos=@($tipos); ciudadPreferida=$ciudad} | Out-Null
    $artista = $evento.participantes[0].id
    if ($Activar) { Api PUT "/usuarios/$($yo.id)/artistas-favoritos/$artista" | Out-Null }
    else { Api DELETE "/usuarios/$($yo.id)/artistas-favoritos/$artista" | Out-Null }
}
function Unificadas([bool]$ComprasCaido=$false) {
    $antes = Api GET "/usuarios/$($yo.id)/preferencias" | ConvertTo-Json -Depth 8 -Compress
    $prefs = Api GET "/usuarios/$($yo.id)/recomendaciones/por-preferencias"
    $historia = if ($ComprasCaido) { @() } else { Api GET "/usuarios/$($yo.id)/recomendaciones/por-compras" }
    $respuesta = Api GET "/usuarios/$($yo.id)/recomendaciones?limite=100"
    $faltantes = if ($ComprasCaido) { 'COMPRAS_EN_COMUN' } else { '' }
    if ($respuesta.parcial -ne $ComprasCaido -or ($respuesta.fuentesNoDisponibles -join '|') -ne $faltantes -or
        $respuesta.historialVerificado -ne (-not $ComprasCaido) -or -not $respuesta.disponibilidadVerificada) {
        throw 'Estado parcial, historial o disponibilidad incorrectos'
    }
    $esperados = @(@($prefs.idEvento) + @($historia.idEvento) | Where-Object { $null -ne $_ } | Sort-Object -Unique)
    $recibidos = @($respuesta.recomendaciones.idEvento | Where-Object { $null -ne $_ })
    if ($recibidos.Count -ne $esperados.Count -or @($recibidos | Select-Object -Unique).Count -ne $recibidos.Count -or
        ($esperados.Count -gt 0 -and @(Compare-Object $esperados $recibidos).Count -ne 0)) { throw 'Union incompleta o eventos duplicados' }
    $previo = $null
    foreach ($r in $respuesta.recomendaciones) {
        $p = $prefs | Where-Object idEvento -eq $r.idEvento | Select-Object -First 1
        $h = $historia | Where-Object idEvento -eq $r.idEvento | Select-Object -First 1
        $puntos = if ($p) { [int]$p.puntuacion } else { 0 }
        $compradores = if ($h) { [long]$h.compradoresEnComun } else { 0L }
        $motivos = @(); $fuentes = @()
        if ($p) { $motivos += @($p.motivos); $fuentes += 'PREFERENCIAS' }
        if ($h) { $motivos += $h.motivo; $fuentes += 'COMPRAS_EN_COMUN' }
        $motivos = @($motivos | Select-Object -Unique)
        if ($r.puntuacion -ne ($puntos + 2*[Math]::Min($compradores,5)) -or $r.puntuacionPreferencias -ne $puntos -or
            $r.compradoresEnComun -ne $compradores -or ($r.motivos -join '|') -ne ($motivos -join '|') -or
            ($r.fuentes -join '|') -ne ($fuentes -join '|')) {
            throw "Combinacion incorrecta para evento $($r.idEvento): puntos=$($r.puntuacion), preferencias=$($r.puntuacionPreferencias)/$puntos, compradores=$($r.compradoresEnComun)/$compradores, fuentes=$($r.fuentes -join '|')/$($fuentes -join '|'), motivos=$($r.motivos -join '|')/$($motivos -join '|')"
        }
        if ($previo -and ($previo.puntuacion -lt $r.puntuacion -or
            ($previo.puntuacion -eq $r.puntuacion -and ([DateTime]$previo.fechaHora -gt [DateTime]$r.fechaHora -or
            ([DateTime]$previo.fechaHora -eq [DateTime]$r.fechaHora -and $previo.idEvento -gt $r.idEvento))))) { throw 'Orden unificado incorrecto' }
        $previo = $r
    }
    $limitada = Api GET "/usuarios/$($yo.id)/recomendaciones?limite=1"
    if (@($limitada.recomendaciones).Count -ne [Math]::Min(1,$recibidos.Count) -or
        ($recibidos.Count -gt 0 -and $limitada.recomendaciones[0].idEvento -ne $recibidos[0])) { throw 'Limite aplicado antes de ordenar o combinar' }
    $despues = Api GET "/usuarios/$($yo.id)/preferencias" | ConvertTo-Json -Depth 8 -Compress
    if ($antes -ne $despues) { throw 'La unificacion modifico preferencias o favoritos' }
}
try {
    $catalogo = Api GET '/api/eventos'
    $ahora = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow,'America/Argentina/Buenos_Aires').DateTime
    foreach ($e in ($catalogo | Sort-Object id)) {
        if ($e.estado -ne 'EN_CURSO' -or $e.estadoPublicacion -ne 'PUBLICADO' -or [DateTime]$e.fechaHora -le $ahora.AddDays(4)) { continue }
        $sectores = Api GET "/eventos/$($e.id)/sectores"
        $sector = $sectores | Where-Object { $_.tipo -eq 'CAMPO' -and $_.disponibles -ge 6 -and -not $_.cancelado } | Select-Object -First 1
        if ($sector) { $inventarios += $sector }
        if ($inventarios.Count -eq 3) { break }
    }
    if ($inventarios.Count -lt 3) { throw 'Se necesitan tres eventos futuros (>4 dias) con sectores CAMPO y al menos 6 cupos libres' }
    $x=$inventarios[0]; $y=$inventarios[1]; $z=$inventarios[2]
    $roles = Api GET '/roles'
    $yo=Cliente 'yo'; $vecino1=Cliente 'vecino1'; $vecino2=Cliente 'vecino2'; $noConfirmado=Cliente 'pendiente'
    $admin=Cliente 'admin' 'ADMINISTRADOR'
    $sinHistorial = Api GET "/usuarios/$($yo.id)/recomendaciones/por-compras"
    if (@($sinHistorial).Count -ne 0) { throw 'Un cliente sin historial no devolvio []' }
    if ($TestUnificadas) {
        Unificadas
        PreferenciasY $true
        Unificadas # Sin compras se usa solamente preferencias.
        PreferenciasY $false
        foreach ($limite in @('0','101','abc')) {
            $r = Invoke-WebRequest "$GatewayUrl/usuarios/$($yo.id)/recomendaciones?limite=$limite" -SkipHttpErrorCheck -TimeoutSec 20
            if ($r.StatusCode -ne 400) { throw 'Limite invalido no devolvio 400' }
        }
        $r = Invoke-WebRequest "$GatewayUrl/usuarios/9223372036854775807/recomendaciones" -SkipHttpErrorCheck -TimeoutSec 20
        if ($r.StatusCode -ne 404) { throw 'Usuario inexistente no devolvio 404' }
    }
    $miX=Comprar $yo $x; $miZ=Comprar $yo $z
    $base = Internas
    $baseFila = $base | Where-Object idEvento -eq $y.idEvento | Select-Object -First 1
    $baseY = if ($baseFila) { [long]$baseFila.compradoresEnComun } else { 0L }
    $v1X=Comprar $vecino1 $x; $v1Z=Comprar $vecino1 $z
    $v1Y=Comprar $vecino1 $y 2
    CantidadY ($baseY+1)
    $v1YRepetida=Comprar $vecino1 $y
    CantidadY ($baseY+1) # Mismo cliente, otra compra y varios eventos comunes: sigue contando una vez.
    $v2X=Comprar $vecino2 $x; $v2Y=Comprar $vecino2 $y
    CantidadY ($baseY+2)
    $pendienteX=Comprar $noConfirmado $x 1 $false
    $sinVinculoY=Comprar $noConfirmado $y
    CantidadY ($baseY+2)
    Api PUT "/compras/$($pendienteX.id)/pago?aprobado=false" | Out-Null
    CantidadY ($baseY+2)
    $reporte.checks.Add('MySQL: union de vecinos y conteo distinto pese a multiples compras, entradas y eventos compartidos')
    $reporte.checks.Add('Compras pendientes y rechazadas no crean vecinos')
    $agregadas = Api GET "/usuarios/$($yo.id)/recomendaciones/por-compras"
    $ry = $agregadas | Where-Object idEvento -eq $y.idEvento | Select-Object -First 1
    $origen = $catalogo | Where-Object id -eq $x.idEvento | Select-Object -First 1
    $destino = $catalogo | Where-Object id -eq $y.idEvento | Select-Object -First 1
    if ($ry.motivo -ne "Clientes que compraron $($origen.nombre) también compraron $($destino.nombre)") { throw 'El motivo no corresponde a los eventos compartidos' }
    $internoPublico = Invoke-WebRequest "$GatewayUrl/interno/recomendaciones/clientes/$($yo.id)" -SkipHttpErrorCheck -TimeoutSec 15
    if ($internoPublico.StatusCode -ne 404) { throw 'Gateway publica el contrato interno de Compras' }
    $reporte.checks.Add('Contrato anonimo, motivo correcto, historial propio excluido y ruta interna fuera del Gateway')

    if ($TestUnificadas) {
        Unificadas # Sin preferencias se usa solamente historial.
        PreferenciasY $true
        Unificadas
        $fusionada = (Api GET "/usuarios/$($yo.id)/recomendaciones?limite=100").recomendaciones | Where-Object idEvento -eq $y.idEvento
        if (@($fusionada).Count -ne 1 -or $fusionada.puntuacionPreferencias -ne 10 -or $fusionada.fuentes.Count -ne 2) { throw 'No se verifico un candidato de ambas fuentes' }
        $reporte.checks.Add('HU6: union real de fuentes, motivos, puntuacion, deduplicacion, orden, limite, vacio, 400 y 404')
    }

    if ($TestInventario) {
        function CandidatoY([bool]$Esperado) {
            $u = Api GET "/usuarios/$($yo.id)/recomendaciones?limite=100"
            if ($u.parcial -or -not $u.disponibilidadVerificada -or (($y.idEvento -in @($u.recomendaciones.idEvento)) -ne $Esperado)) {
                throw 'El filtro comun no refleja el inventario de Y'
            }
            foreach ($fuente in @('por-preferencias','por-compras')) {
                $lista = Api GET "/usuarios/$($yo.id)/recomendaciones/$fuente"
                if (($y.idEvento -in @($lista.idEvento)) -ne $Esperado) { throw "Inventario incorrecto en $fuente" }
            }
        }
        $stockInicial = Api GET "/eventos/$($y.idEvento)/sectores"
        if (($stockInicial | Measure-Object disponibles -Sum).Sum -gt 50) { throw 'La prueba de inventario requiere como maximo 50 entradas libres en el evento candidato' }
        $loteHttp = Invoke-WebRequest "$SectoresUrl/interno/inventario/disponibilidad" -Method Post -ContentType 'application/json' -TimeoutSec 20 -Body (
            @{idsEventos=@($y.idEvento,$y.idEvento,$x.idEvento,9223372036854775807L)} | ConvertTo-Json)
        $lote = $loteHttp.Content | ConvertFrom-Json
        if (@($lote).Count -ne 3 -or $loteHttp.Headers['Cache-Control'] -notcontains 'no-store' -or
            ($lote | Where-Object idEvento -eq 9223372036854775807L).disponible) { throw 'Contrato por lote incorrecto' }
        CandidatoY $true
        try {
            $contador=0
            foreach ($sector in $stockInicial) {
                $libres = @($sector.asientos | Where-Object estado -eq 'DISPONIBLE')
                for ($indice=0; $indice -lt $sector.disponibles; $indice++) {
                    $clienteReserva = Cliente "inventario$contador"
                    $ids = if ($sector.tipo -eq 'NUMERADO') { @($libres[$indice].idAsiento) } else { @() }
                    $reserva = Api POST '/reservas' @{claveIdempotencia="hu9-$tag-$contador";idCliente=$clienteReserva.id;
                        idEvento=$y.idEvento;idSector=$sector.idSector;cantidad=1;idAsientos=@($ids)}
                    $reservasInventario.Add($reserva); $contador++
                }
            }
            CandidatoY $false
            $catalogoActual = Api GET '/api/eventos'
            $estadoCatalogo = $catalogoActual | Where-Object id -eq $y.idEvento | Select-Object -First 1
            if ($estadoCatalogo.estado -ne 'EN_CURSO') { throw 'La prueba debe agotar cupos mediante reservas sin cambiar el estado del catalogo' }
            Api POST "/reservas/$($reservasInventario[0].id)/liberacion" | Out-Null
            CandidatoY $true
        } finally {
            foreach ($reserva in $reservasInventario) { Api POST "/reservas/$($reserva.id)/liberacion" | Out-Null }
        }
        foreach ($sector in $stockInicial) {
            $actual = Api GET "/eventos/$($y.idEvento)/sectores/$($sector.idSector)/disponibilidad"
            if ($actual.disponibles -ne $sector.disponibles -or $actual.reservados -ne $sector.reservados -or
                $actual.vendidos -ne $sector.vendidos) { throw 'Las reservas de HU9 no restituyeron el inventario' }
        }
        if ($TestOutage) {
            try {
                & docker compose -f $rootCompose stop sectores
                if ($LASTEXITCODE -ne 0) { throw 'No se pudo detener Sectores' }
                $u = Api GET "/usuarios/$($yo.id)/recomendaciones?limite=100"
                if (-not $u.parcial -or $u.disponibilidadVerificada -or -not $u.historialVerificado -or
                    $y.idEvento -notin @($u.recomendaciones.idEvento)) { throw 'Caida de inventario no informa disponibilidad no verificada' }
                $p = Invoke-WebRequest "$GatewayUrl/usuarios/$($yo.id)/recomendaciones/por-preferencias" -TimeoutSec 25
                if ($p.Headers['X-Disponibilidad-Verificada'] -notcontains 'false' -or $p.Headers['X-Respuesta-Parcial'] -notcontains 'true') {
                    throw 'Gateway no conserva las cabeceras de inventario no verificado'
                }
            } finally {
                & docker compose -f $rootCompose up -d --wait --wait-timeout 180 sectores
                if ($LASTEXITCODE -ne 0) { throw 'No se pudo restaurar Sectores' }
            }
            $recuperado=$false
            for ($intento=0; $intento -lt 15; $intento++) {
                $u = Api GET "/usuarios/$($yo.id)/recomendaciones?limite=100"
                if ($u.disponibilidadVerificada -and -not $u.parcial) { $recuperado=$true; break }
                Start-Sleep -Seconds 2
            }
            if (-not $recuperado) { throw 'No se recupero la verificacion de inventario' }
            CandidatoY $true
            $reporte.inventoryOutageVerified=$true
        }
        $reporte.inventoryVerified=$true
        $reporte.checks.Add('HU9: lote, reservas agotan ambas fuentes, liberar una entrada restaura candidato, inventario restituido')
    }

    if ($TestOutage) {
        foreach ($servicio in @('compras','eventos')) {
            try {
                & docker compose -f $rootCompose stop $servicio
                if ($LASTEXITCODE -ne 0) { throw "No se pudo detener $servicio" }
                $r = Invoke-WebRequest "$GatewayUrl/usuarios/$($yo.id)/recomendaciones/por-compras" -SkipHttpErrorCheck -TimeoutSec 20
                if ($r.StatusCode -ne 503) { throw "La caida de $servicio no devolvio 503" }
                if ($TestUnificadas) {
                    if ($servicio -eq 'compras') { Unificadas $true }
                    else {
                        $u = Invoke-WebRequest "$GatewayUrl/usuarios/$($yo.id)/recomendaciones" -SkipHttpErrorCheck -TimeoutSec 25
                        if ($u.StatusCode -ne 503) { throw 'Ambas fuentes inaccesibles no devolvieron 503' }
                    }
                }
            } finally {
                & docker compose -f $rootCompose up -d --wait --wait-timeout 180 $servicio
                if ($LASTEXITCODE -ne 0) { throw "No se pudo restaurar $servicio" }
            }
            $ok = $false
            for ($intento=0; $intento -lt 15; $intento++) {
                $r = Invoke-WebRequest "$GatewayUrl/usuarios/$($yo.id)/recomendaciones/por-compras" -SkipHttpErrorCheck -TimeoutSec 20
                if ($r.StatusCode -eq 200) { $ok=$true; break }
                if ($r.StatusCode -ne 503) { throw 'Error inesperado durante la recuperacion' }
                Start-Sleep -Seconds 2
            }
            if (-not $ok) { throw "No se recupero la consulta con $servicio" }
            CantidadY ($baseY+2)
            if ($TestUnificadas) { Unificadas }
        }
        $reporte.outageVerified=$true
        $reporte.checks.Add('Caida y recuperacion de Compras y Eventos: 503 sin perder historial')
    }

    $entradasY = Api GET "/entradas/compra/$($v1Y.id)"
    Reintegrar $entradasY[0]; CantidadY ($baseY+2) # Quedan entradas de este cliente en Y.
    Reintegrar $entradasY[1]; CantidadY ($baseY+2) # Queda su segunda compra en Y.
    $entradasRepetidas = Api GET "/entradas/compra/$($v1YRepetida.id)"
    Reintegrar $entradasRepetidas[0]; CantidadY ($baseY+1)
    $origenVecino = Api GET "/entradas/compra/$($v2X.id)"
    Reintegrar $origenVecino[0]; CantidadY $baseY
    $reporte.checks.Add('Reintegros parciales conservan al vecino; reintegro total o del vinculo lo excluye')
    if ($TestUnificadas) {
        $propiaY = Comprar $yo $y 2
        $otraPropiaY = Comprar $yo $y
        function HistorialY([bool]$Presente) {
            $h = Invoke-RestMethod "$ComprasUrl/interno/recomendaciones/clientes/$($yo.id)/historial-valido" -TimeoutSec 20
            if (@($h.PSObject.Properties.Name).Count -ne 1 -or $null -eq $h.idsEventos -or
                @($h.idsEventos | Select-Object -Unique).Count -ne @($h.idsEventos).Count -or
                (($y.idEvento -in $h.idsEventos) -ne $Presente)) { throw 'Historial valido incorrecto o no anonimo' }
            $u = Api GET "/usuarios/$($yo.id)/recomendaciones?limite=100"
            $p = Api GET "/usuarios/$($yo.id)/recomendaciones/por-preferencias"
            if (-not $u.historialVerificado -or $u.parcial -or
                (($y.idEvento -in @($u.recomendaciones.idEvento)) -eq $Presente) -or
                (($y.idEvento -in @($p.idEvento)) -eq $Presente)) { throw 'No se aplico la exclusion comun o no se restauro el evento reintegrado' }
            Unificadas
        }
        HistorialY $true
        $propias = Api GET "/entradas/compra/$($propiaY.id)"
        Reintegrar $propias[0]; HistorialY $true
        Reintegrar $propias[1]; HistorialY $true # Sigue la otra compra del mismo evento.
        $otraEntrada = Api GET "/entradas/compra/$($otraPropiaY.id)"
        Reintegrar $otraEntrada[0]; HistorialY $false
        $oculta = Invoke-WebRequest "$GatewayUrl/interno/recomendaciones/clientes/$($yo.id)/historial-valido" -SkipHttpErrorCheck -TimeoutSec 15
        if ($oculta.StatusCode -ne 404) { throw 'El historial interno quedo expuesto por Gateway' }
        $reporte.historyVerified=$true
        $reporte.checks.Add('HU8: historial propio sin duplicados, exclusion comun, reintegro parcial y total, evento recomendado nuevamente')
    }
    foreach ($c in @($miX,$miZ)) { $es=Api GET "/entradas/compra/$($c.id)"; foreach($e in $es){Reintegrar $e} }
    $sinHistorial = Api GET "/usuarios/$($yo.id)/recomendaciones/por-compras"
    if (@($sinHistorial).Count -ne 0) { throw 'Tras reintegrar todo el historial no devolvio []' }
    $reporte.checks.Add('Historial propio totalmente reintegrado devuelve []')
    if ($TestUnificadas) {
        Unificadas
        if ($TestOutage) {
            try {
                & docker compose -f $rootCompose stop eventos
                if ($LASTEXITCODE -ne 0) { throw 'No se pudo detener Eventos' }
                $u = Api GET "/usuarios/$($yo.id)/recomendaciones"
                if (-not $u.parcial -or ($u.fuentesNoDisponibles -join '|') -ne 'PREFERENCIAS' -or @($u.recomendaciones).Count -ne 0) {
                    throw 'Una fuente exitosa vacia y otra caida debe devolver 200 parcial vacio'
                }
            } finally {
                & docker compose -f $rootCompose up -d --wait --wait-timeout 180 eventos
                if ($LASTEXITCODE -ne 0) { throw 'No se pudo restaurar Eventos' }
            }
            for ($intento=0; $intento -lt 15; $intento++) {
                $u = Api GET "/usuarios/$($yo.id)/recomendaciones"
                if (-not $u.parcial) { break }
                Start-Sleep -Seconds 2
            }
            if ($u.parcial) { throw 'No se recupero la fuente de preferencias' }
        }
        PreferenciasY $false
        Unificadas
        $reporte.unifiedVerified=$true
        if ($TestOutage) {
            $reporte.checks.Add('HU6: caida de una fuente con datos disponibles, ambas inaccesibles 503, fuente vacia exitosa y recuperacion')
        } else {
            $reporte.checks.Add('HU6: fuentes combinadas y fuentes individuales, sin interrumpir servicios')
        }
    }
    $reporte.passed=$true
} finally {
    $errores = [Collections.Generic.List[string]]::new()
    foreach ($reserva in $reservasInventario) {
        try { Api POST "/reservas/$($reserva.id)/liberacion" | Out-Null }
        catch { $errores.Add("Reserva temporal $($reserva.id): $($_.Exception.Message)") }
    }
    foreach ($c in $operaciones) {
        try {
            $actual = Api GET "/compras/$($c.id)"
            if ($actual.estadoCompra -eq 'PENDIENTE') { Api PUT "/compras/$($c.id)/pago?aprobado=false" | Out-Null }
            if ($actual.estadoCompra -eq 'APROBADA') {
                $es = Api GET "/entradas/compra/$($c.id)"
                foreach ($e in $es) { Reintegrar $e }
            }
        } catch { $errores.Add("Compra temporal $($c.id): $($_.Exception.Message)") }
    }
    foreach ($i in $inventarios) {
        try {
            $actual = Api GET "/eventos/$($i.idEvento)/sectores/$($i.idSector)/disponibilidad"
            if ($actual.disponibles -ne $i.disponibles -or $actual.reservados -ne $i.reservados -or $actual.vendidos -ne $i.vendidos) {
                throw 'El inventario no coincide con el estado inicial'
            }
        } catch { $errores.Add("Inventario $($i.idEvento): $($_.Exception.Message)") }
    }
    if ($errores.Count -eq 0) {
        foreach ($c in $clientes) {
            try { Api DELETE "/usuarios/$($c.id)" | Out-Null } catch { $errores.Add($_.Exception.Message) }
        }
    }
    $reporte.cleanupErrors=@($errores.ToArray())
    $reporte.cleanupPassed=($errores.Count -eq 0)
    $reporte.checkedAt=[DateTimeOffset]::Now.ToString('o')
    $reporte | ConvertTo-Json -Depth 8 | Set-Content $out
    if ($errores.Count) { throw ($errores -join '; ') }
}
Write-Output 'OK: compras en comun verificadas en MySQL por Gateway; entradas reintegradas e inventario restituido.'
