#requires -Version 7.0
param([int]$CantidadEventos=10000, [int]$Consultas=100, [double]$MaxP95Ms=2000)
$ErrorActionPreference='Stop'
if ($CantidadEventos -lt 10000 -or $Consultas -lt 100) { throw 'La prueba acordada requiere al menos 10000 eventos y 100 consultas' }
$raiz=(Resolve-Path "$PSScriptRoot/../..").Path
$composeRaiz=Join-Path $raiz 'docker-compose.yml'
$compose=Join-Path $PSScriptRoot 'compose.hu10.yml'
$tag=[guid]::NewGuid().ToString('N').Substring(0,12)
$proyecto="ticketya-hu10-$tag"
$dbEventos="hu10_${tag}_eventos"; $dbSectores="hu10_${tag}_sectores"; $dbUsuarios="hu10_${tag}_usuarios"
$cuenta="hu10_$tag"
if ($dbEventos -notmatch '^hu10_[a-f0-9]{12}_eventos$' -or $dbSectores -notmatch '^hu10_[a-f0-9]{12}_sectores$' -or
    $dbUsuarios -notmatch '^hu10_[a-f0-9]{12}_usuarios$' -or $cuenta -notmatch '^hu10_[a-f0-9]{12}$') { throw 'Nombres temporales invalidos' }
$variables=@{HU10_RUN=$tag;HU10_EVENTOS_DB=$dbEventos;HU10_SECTORES_DB=$dbSectores;HU10_USUARIOS_DB=$dbUsuarios;
    HU10_DB_USER=$cuenta;HU10_DB_PASSWORD="hu10_test_$tag"}
$anteriores=@{}
foreach($nombre in $variables.Keys){$anteriores[$nombre]=[Environment]::GetEnvironmentVariable($nombre,'Process');[Environment]::SetEnvironmentVariable($nombre,$variables[$nombre],'Process')}
$salida=Join-Path $raiz '.local/system-tests/hu10-performance-result.json'
New-Item -ItemType Directory -Force (Split-Path $salida)|Out-Null
$reporte=@{passed=$false;totalEventos=$CantidadEventos;consultas=$Consultas;objetivoP95Ms=$MaxP95Ms;
    concurrencia=1;calentamiento=10;datosAislados=$true;proveedoresSimulados=$false}
function Sql([string]$Texto) {
    $Texto | & docker compose -f $composeRaiz exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4'
    if($LASTEXITCODE -ne 0){throw 'Fallo SQL en bases temporales de HU10'}
}
function Api([string]$Metodo,[string]$Url,$Body=$null){
    $http=@{Method=$Metodo;Uri=$Url;TimeoutSec=40}
    if($null -ne $Body){$http.ContentType='application/json';$http.Body=$Body|ConvertTo-Json -Depth 10}
    Invoke-RestMethod @http
}
function Puerto([string]$Servicio,[int]$Puerto){
    $direccion=& docker compose -p $proyecto -f $compose port $Servicio $Puerto
    if($LASTEXITCODE -ne 0){throw 'No se encontro el puerto temporal'}
    return "http://$($direccion.Trim())"
}
try {
    Sql "CREATE DATABASE $dbEventos CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE DATABASE $dbSectores CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '$cuenta'@'%' IDENTIFIED BY '$($variables.HU10_DB_PASSWORD)'; GRANT ALL ON $dbEventos.* TO '$cuenta'@'%'; GRANT ALL ON $dbSectores.* TO '$cuenta'@'%';"
    & docker compose -p $proyecto -f $compose up -d --wait --wait-timeout 240
    if($LASTEXITCODE -ne 0){throw 'No se inicio el entorno temporal HU10'}
    $gateway=Puerto gateway 8080; $eventos=Puerto eventos 8005; $sectores=Puerto sectores 8002
    $fecha=[DateTime]::Now.AddDays(10).ToString('yyyy-MM-dd')+'T20:00:00'
    $sqlFecha=$fecha.Replace('T',' ')
    Sql "USE $dbEventos; INSERT INTO lugares(id,nombre,ciudad,ciudad_normalizada) VALUES(1,'Mendoza','Mendoza','mendoza'),(2,'Cordoba',' CÓRDOBA ','cordoba'); INSERT INTO artistas(id,nombre,genero) VALUES(1,'Favorito','Rock'),(2,'Otro','Jazz'); USE $dbSectores; INSERT INTO sectores(id,id_lugar,nombre,tipo,capacidad_total) VALUES(1,1,'Campo','CAMPO',10),(2,2,'Campo Cordoba','CAMPO',10);"
    $esperados=[Collections.Generic.List[object]]::new()
    for($inicio=1;$inicio -le $CantidadEventos;$inicio+=1000){
        $valores=[Collections.Generic.List[string]]::new();$vinculos=[Collections.Generic.List[string]]::new()
        $cabeceras=[Collections.Generic.List[string]]::new();$stocks=[Collections.Generic.List[string]]::new()
        $fin=[Math]::Min($inicio+999,$CantidadEventos)
        for($id=$inicio;$id -le $fin;$id++){
            $tipo=if($id%500 -eq 2 -or $id -eq $CantidadEventos){'RECITAL'}else{'OTRO'}
            $lugar=if($id%500 -eq 1 -or $id -eq $CantidadEventos){2}else{1}
            $artista=if($id%500 -eq 0){1}else{2}
            $valores.Add("($id,'Evento $id','Carga HU10','$tipo',1,'$sqlFecha',$lugar,'EN_CURSO','PUBLICADO',false)")
            $vinculos.Add("($id,$artista)")
            $puntos=0;$motivos=@()
            if($artista -eq 1){$puntos+=5;$motivos+='Participa un artista favorito'}
            if($tipo -eq 'RECITAL'){$puntos+=3;$motivos+='Coincide con un tipo de evento preferido'}
            if($lugar -eq 2){$puntos+=2;$motivos+='Se realiza en tu ciudad de preferencia'}
            if($puntos -gt 0){
                $cabeceras.Add("($id,$lugar,'$sqlFecha',10,false)")
                $stocks.Add("($id,$id,$lugar,10.00,10,0,0,0)")
                $esperados.Add([pscustomobject]@{idEvento=[long]$id;nombre="Evento $id";tipo=$tipo;fechaHora=$fecha;
                    ciudad=if($lugar -eq 2){' CÓRDOBA '}else{'Mendoza'};puntuacion=$puntos;puntuacionPreferencias=$puntos;
                    compradoresEnComun=0;motivos=@($motivos);fuentes=@('PREFERENCIAS')})
            }
        }
        Sql "USE $dbEventos; INSERT INTO eventos(id,nombre,descripcion,tipo,id_organizador,fecha_hora,id_lugar,estado,estado_publicacion,demanda) VALUES $($valores -join ','); INSERT INTO evento_artista(id_evento,id_artista) VALUES $($vinculos -join ',');"
        if($cabeceras.Count){Sql "USE $dbSectores; INSERT INTO inventarios_evento(id,id_lugar,fecha_hora,limite_por_cliente,cancelado) VALUES $($cabeceras -join ','); INSERT INTO sectores_evento(id,id_evento,id_sector,precio,capacidad_habilitada,lugares_reservados,lugares_vendidos,version) VALUES $($stocks -join ',');"}
    }
    $semilla=@'
db.getSiblingDB('__DB__').secuencias.updateOne({_id:'usuarios'},{$set:{valor:NumberLong('1000000000000')}},{upsert:true});
'@
    & docker compose -f $composeRaiz exec -T mongodb mongosh --quiet --eval ($semilla.Replace('__DB__',$dbUsuarios)) | Out-Null
    if($LASTEXITCODE -ne 0){throw 'No se preparo la secuencia Mongo temporal'}
    $roles=Api GET "$gateway/roles"; $rol=$roles|Where-Object name -eq 'CLIENTE'|Select-Object -First 1
    $usuario=Api POST "$gateway/usuarios" @{nombre='Rendimiento HU10';email="hu10-$tag@example.com";dni=$tag;ciudad='Mendoza';idRol=$rol.id}
    $historial=Api GET "http://localhost:8003/interno/recomendaciones/clientes/$($usuario.id)/historial-valido"
    if(@($historial.idsEventos).Count -ne 0){throw 'El ID temporal debe carecer de compras reales'}
    Api PUT "$gateway/usuarios/$($usuario.id)/preferencias" @{tiposEventoPreferidos=@('RECITAL');ciudadPreferida='cordoba'}|Out-Null
    Api PUT "$gateway/usuarios/$($usuario.id)/artistas-favoritos/1"|Out-Null
    # Tres candidatos siguen publicados pero tienen sus diez lugares retenidos por reservas reales.
    $agotados=@($esperados|Where-Object idEvento -ne $CantidadEventos|Select-Object -First 3)
    foreach($evento in $agotados){
        $idSector=if($evento.ciudad -eq 'Mendoza'){1}else{2}
        Api POST "$sectores/reservas" @{claveIdempotencia="hu10-$tag-$($evento.idEvento)";idCliente=1000000000002L;
            idEvento=$evento.idEvento;idSector=$idSector;cantidad=10;idAsientos=@()}|Out-Null
    }
    $todosEsperados=@($esperados|Where-Object {$_.idEvento -notin @($agotados.idEvento)}|Sort-Object @{Expression='puntuacion';Descending=$true},fechaHora,idEvento)
    $esperadosLimitados=@($todosEsperados|Select-Object -First 10)
    # Baseline completo solo desde el ejecutor, nunca desde Usuarios.
    $catalogo=Api GET "$eventos/api/eventos"
    if(@($catalogo).Count -ne $CantidadEventos){throw 'El catalogo de prueba no tiene la cantidad requerida'}
    $baseline=[Collections.Generic.List[object]]::new()
    foreach($evento in $catalogo){
        if($evento.estado -ne 'EN_CURSO' -or $evento.estadoPublicacion -ne 'PUBLICADO' -or [DateTime]$evento.fechaHora -le [DateTime]::Now){continue}
        $puntos=0;$motivos=@()
        if(1 -in @($evento.participantes.id)){$puntos+=5;$motivos+='Participa un artista favorito'}
        if($evento.tipo -eq 'RECITAL'){$puntos+=3;$motivos+='Coincide con un tipo de evento preferido'}
        $ciudad=(($evento.lugar.ciudad.Normalize([Text.NormalizationForm]::FormD)-replace '\p{M}','').Trim()-replace '\s+',' ').ToLowerInvariant()
        if($ciudad -eq 'cordoba'){$puntos+=2;$motivos+='Se realiza en tu ciudad de preferencia'}
        if($puntos -gt 0){$baseline.Add([pscustomobject]@{idEvento=[long]$evento.id;nombre=$evento.nombre;tipo=$evento.tipo;
            fechaHora=$evento.fechaHora;ciudad=$evento.lugar.ciudad;puntuacion=$puntos;puntuacionPreferencias=$puntos;
            compradoresEnComun=0;motivos=@($motivos);fuentes=@('PREFERENCIAS')})}
    }
    if((($baseline.idEvento|Sort-Object) -join '|') -ne (($esperados.idEvento|Sort-Object) -join '|')){throw 'El baseline del catalogo completo difiere del conjunto sembrado'}
    $esperadosLimitados=@($baseline|Where-Object {$_.idEvento -notin @($agotados.idEvento)}|
        Sort-Object @{Expression='puntuacion';Descending=$true},fechaHora,idEvento|Select-Object -First 10)
    $filtrados=Api POST "$eventos/interno/eventos/recomendaciones/candidatos" @{tiposEventoPreferidos=@('RECITAL');artistasFavoritos=@(1);ciudadPreferida='cordoba';fechaDesde=[DateTime]::Now.ToString('s')}
    if(@($filtrados).Count -ne $esperados.Count){throw 'La consulta OR no coincide con el baseline del catalogo completo'}
    $idsLote=@($agotados.idEvento)+@([long]$CantidadEventos)
    $porIds=Api POST "$eventos/interno/eventos/recomendaciones/por-ids" @{idsEventos=@($idsLote)}
    if(@($porIds).Count -ne $idsLote.Count){throw 'El lote de eventos no coincide con los IDs solicitados'}
    $uri="$gateway/usuarios/$($usuario.id)/recomendaciones?limite=10"
    function Verificar($respuesta){
        if($respuesta.parcial -or -not $respuesta.historialVerificado -or -not $respuesta.disponibilidadVerificada){throw 'La respuesta del benchmark debe estar completamente verificada'}
        if(@($respuesta.recomendaciones).Count -ne $esperadosLimitados.Count){throw 'Cantidad de recomendaciones inesperada'}
        for($indice=0;$indice -lt $esperadosLimitados.Count;$indice++){
            $actual=$respuesta.recomendaciones[$indice];$esperado=$esperadosLimitados[$indice]
            foreach($campo in @('idEvento','nombre','tipo','ciudad','puntuacion','puntuacionPreferencias','compradoresEnComun')){
                if($actual.$campo -ne $esperado.$campo){throw "Baseline diferente en $campo"}
            }
            if([DateTime]$actual.fechaHora -ne [DateTime]$esperado.fechaHora -or
                ($actual.motivos -join '|') -ne ($esperado.motivos -join '|') -or ($actual.fuentes -join '|') -ne 'PREFERENCIAS'){
                throw 'Fecha, motivos o fuentes diferentes del baseline'
            }
        }
    }
    for($i=0;$i -lt 10;$i++){Verificar (Api GET $uri)}
    $tiempos=[Collections.Generic.List[double]]::new()
    for($i=0;$i -lt $Consultas;$i++){
        $reloj=[Diagnostics.Stopwatch]::StartNew();$respuesta=Api GET $uri;$reloj.Stop()
        $tiempos.Add($reloj.Elapsed.TotalMilliseconds);Verificar $respuesta
        if(($i+1)%20 -eq 0){Write-Output "HU10: $($i+1)/$Consultas consultas verificadas"}
    }
    $ordenados=@($tiempos|Sort-Object);$p95=$ordenados[[Math]::Ceiling($Consultas*0.95)-1]
    $reporte.p95Ms=[Math]::Round($p95,2);$reporte.p50Ms=[Math]::Round($ordenados[[Math]::Ceiling($Consultas*0.5)-1],2)
    $reporte.maxMs=[Math]::Round($ordenados[-1],2);$reporte.candidatosPreferencias=$esperados.Count
    $reporte.agotadosExcluidos=$agotados.Count;$reporte.resultadosPorConsulta=$esperadosLimitados.Count
    $reporte.baselineCoincidente=$true;$reporte.passed=($p95 -le $MaxP95Ms)
    if(-not $reporte.passed){throw "p95 $p95 ms supera el objetivo de $MaxP95Ms ms"}
} finally {
    $errores=[Collections.Generic.List[string]]::new()
    & docker compose -p $proyecto -f $compose down
    if($LASTEXITCODE -ne 0){$errores.Add('No se pudieron retirar los contenedores temporales')}
    try{Sql "DROP DATABASE IF EXISTS $dbEventos; DROP DATABASE IF EXISTS $dbSectores; DROP USER IF EXISTS '$cuenta'@'%';"}catch{$errores.Add($_.Exception.Message)}
    & docker compose -f $composeRaiz exec -T mongodb mongosh --quiet --eval "db.getSiblingDB('$dbUsuarios').dropDatabase()"|Out-Null
    if($LASTEXITCODE -ne 0){$errores.Add('No se pudo retirar la base Mongo temporal')}
    foreach($nombre in $variables.Keys){[Environment]::SetEnvironmentVariable($nombre,$anteriores[$nombre],'Process')}
    $reporte.cleanupPassed=($errores.Count -eq 0);$reporte.cleanupErrors=@($errores.ToArray());$reporte.checkedAt=[DateTimeOffset]::Now.ToString('o')
    $reporte|ConvertTo-Json -Depth 10|Set-Content $salida
    if($errores.Count){throw ($errores -join '; ')}
}
Write-Output "OK HU10: p95=$($reporte.p95Ms) ms; objetivo=$MaxP95Ms ms; bases y contenedores temporales retirados."
