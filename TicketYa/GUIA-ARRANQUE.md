# Guía de arranque y prueba de TicketYa

## Sistema completo en contenedores

Para ejecutar las cinco aplicaciones y las dos bases sin IntelliJ: `docker compose up --build -d --wait --wait-timeout 300` desde la raíz. Consultar [DOCKER.md](DOCKER.md) para puertos, datos existentes, configuración y comandos. Las secciones siguientes describen la alternativa manual con Java en el equipo; en ese caso levantar únicamente `docker compose up -d --wait mysql` y la base de Compras según la sección 3.

Actualizada al 10 de noviembre de 2026. Instrucciones portables para Windows, Linux y macOS, con MySQL compartido, una base propia para Compras y JWT apartado en `futura jwt`.

Usuarios, Eventos, SectorAsiento, Compras y Gateway tienen un proyecto Spring Boot ejecutable. El Compose raíz ahora incluye las cinco aplicaciones y las dos bases. Para la alternativa manual de esta guía se selecciona únicamente el servicio `mysql` y se usa el Compose separado de Compras en 3311; las aplicaciones Java se ejecutan desde una terminal o IntelliJ.

## 1. Requisitos y carpeta de trabajo

Esta guía sirve para **Windows, Linux y macOS**, independientemente del nombre de usuario, disco o ubicación del proyecto.

- JDK 21 o superior, con `java` y `javac` disponibles en la terminal.
- Docker Desktop en Windows/macOS, o Docker Engine en Linux, con contenedores Linux y Docker Compose v2 o superior. Los Compose de compatibilidad dentro de cada microservicio requieren Compose 2.20 o superior; se recomienda usar siempre el de la raíz. La excepción es Compras, que usa su propio `compose.mysql.yml` porque su base no está en el contenedor compartido.
- Conexión a internet en la primera ejecución para descargar MySQL y las dependencias de Maven.
- Una terminal para Docker/pruebas y otra por cada aplicación Java.
- Postman, si se prefiere probar sin comandos HTTP.
- PowerShell 7.3 o superior **solo para los scripts `.ps1` de migración/verificación y el ejemplo automatizado de la sección 9**. También está disponible en Linux y macOS. No hace falta para el arranque normal con Bash/Zsh.

Clonar o descargar el proyecto donde se prefiera. Abrir en el editor o explorador la carpeta que contiene `docker-compose.yml`, `msv-usuario`, `msv-evento`, `msv-SectoerAsiento`, `msv-compraEntardaSol` y `api-gateway`; esa es la **raíz del repositorio**.

Todos los comandos Docker de esta guía parten de esa raíz. Cada bloque de arranque Java parte de una **terminal nueva abierta en la raíz**. Los nombres de carpeta son relativos al repositorio; no se necesita ninguna ruta personal.

Comprobar en cualquier terminal:

```sh
java -version
javac -version
docker version
docker compose version
```

Java y javac deben corresponder a un JDK 21 o superior. Docker debe mostrar también la sección Server. En Windows/macOS, abrir Docker Desktop y esperar a que esté listo. En Linux, iniciar Docker Engine mediante el mecanismo de la distribución y usar una cuenta con acceso al daemon; no es necesario Docker Desktop.

### JAVA_HOME sin depender de una ruta fija

Si JAVA_HOME ya apunta al JDK correcto, no cambiarlo. Si falta, estos ejemplos obtienen la ubicación del Java que ya está instalado y configurado en PATH. Ejecutarlos en cada terminal de arranque que lo necesite; solo afectan esa terminal.

**PowerShell:**

```powershell
$lineaJavaHome = java -XshowSettings:properties -version 2>&1 |
    Select-String '^\s*java\.home\s*=' |
    Select-Object -First 1
if (-not $lineaJavaHome) { throw 'No se pudo localizar el JDK desde PATH.' }
$env:JAVA_HOME = ($lineaJavaHome.ToString() -split '=', 2)[1].Trim()
& (Join-Path $env:JAVA_HOME 'bin/javac') -version
```

**Linux/macOS con Bash o Zsh:**

```bash
export JAVA_HOME="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.home = //p' | head -n 1)"
"$JAVA_HOME/bin/javac" -version
```

Si javac no existe en esa ubicación, instalar/configurar un JDK completo; un JRE solo no alcanza. Si hay varias versiones instaladas, seleccionar la correcta en PATH o en el selector de JDK del sistema antes de obtener JAVA_HOME.

Los archivos Maven Wrapper ya vienen en cada módulo. En Windows se usa `mvnw.cmd`; en Linux/macOS se usa `sh ./mvnw`, que evita depender del permiso de ejecución del archivo.

## 2. Puertos actuales

| Componente | Puerto local | Base / destino |
| --- | --- | --- |
| MySQL compartido: Usuarios | 3306 | usuarios |
| MySQL compartido: Eventos | 3306 | eventos |
| MySQL compartido: SectorAsiento | 3306 | sectores_asientos |
| Usuarios | 8004 | MySQL en 3306 |
| Eventos | 8005 | MySQL en 3306 |
| SectorAsiento | 8002 | MySQL en 3306; Feign a Eventos en 8005 |
| Gateway | 8080 | Usuarios en 8004, SectorAsiento en 8002 y Compras en 8003 |
| Compras | 8003 | MySQL propio en 3311; Feign a Eventos en 8005 y SectorAsiento en 8002 |
| MySQL propio de Compras | 3311 | compra_entrada_sol |

Orden recomendado: **Docker → bases compartidas → Usuarios y Eventos → SectorAsiento → Compras → Gateway → pruebas**.

No se requiere JWT. `/auth/login` y `/auth/registro` están retirados; los perfiles se crean con `POST /usuarios`. La carpeta `futura jwt` conserva la implementación para otra etapa.

## 3. Levantar las tres bases en un solo contenedor

En una terminal abierta en la raíz del repositorio (mismos comandos en todos los sistemas):

```sh
docker compose up -d --wait mysql
docker compose ps
```

Debe aparecer un único servicio mysql saludable. Dentro están usuarios, eventos y sectores_asientos, todos accesibles en localhost:3306.

| Base | Usuario local | Contraseña local |
| --- | --- | --- |
| usuarios | usuario_app | usuario_dev |
| eventos | evento_app | evento_dev |
| sectores_asientos | sector_app | sector_dev |

Cada cuenta tiene permisos solo sobre su esquema. Los application.properties ya están actualizados. No hace falta crear las bases a mano: se inicializan al crear el volumen; las tablas las crea Hibernate al iniciar cada microservicio.

### Base propia de Compras

Compras no comparte el contenedor anterior: usa su propio Compose, publicado en 3311, para no tocar las bases de los demás servicios.

```sh
docker compose -f ./msv-compraEntardaSol/compose.mysql.yml up -d --wait
docker compose -f ./msv-compraEntardaSol/compose.mysql.yml ps
```

| Base | Usuario local | Contraseña local |
| --- | --- | --- |
| compra_entrada_sol | compra_app | compra_dev |

El esquema `compra_entrada_sol` se crea al iniciar el contenedor y las tablas las crea Hibernate con `DDL_AUTO=update`. El compose raíz no crea esta base; por eso el módulo trae el suyo.

**Si ya tenías datos en los contenedores anteriores, migrar antes de iniciar las aplicaciones Java contra el nuevo contenedor:**

```sh
pwsh -File ./scripts/Migrar-Bases.ps1 -DetenerOrigenes
```

El script requiere PowerShell 7.3 o superior en cualquiera de los sistemas operativos. Respalda los datos, importa únicamente a esquemas vacíos, compara tablas y filas y después detiene los contenedores antiguos. No borra sus volúmenes. Si no hay datos previos, se puede omitir este paso. Los contenedores anteriores no se detienen automáticamente con el simple comando up.

Para comprobar las tres cuentas y el aislamiento de permisos, con PowerShell 7.3 o superior:

```sh
pwsh -File ./scripts/Verificar-Bases.ps1
```

Los detalles de migración, personalización de credenciales y puertos están en [docker/mysql/README.md](docker/mysql/README.md). Si 3306 está ocupado, configurar MYSQL_PORT y las DB_URL correspondientes como indica ese documento. Las variables de un archivo .env de Docker no se cargan automáticamente en las aplicaciones Java ni en IntelliJ. Los puertos y contraseñas de la tabla son valores de desarrollo configurados en el proyecto; no dependen del equipo. Si se personalizan, aplicar los mismos valores al contenedor y a las aplicaciones.

## 4. Iniciar Usuarios

**Windows con PowerShell**, desde una terminal nueva en la raíz:

```powershell
cd ./msv-usuario
.\mvnw.cmd spring-boot:run
```

**Linux/macOS con Bash/Zsh**, desde una terminal nueva en la raíz:

```bash
cd ./msv-usuario
sh ./mvnw spring-boot:run
```

Si se usa PowerShell en Linux/macOS, ejecutar `sh ./mvnw spring-boot:run` en lugar de `mvnw.cmd`. JAVA_HOME debe estar configurado en esa terminal según la sección 1. Las URLs locales ya tienen los valores por defecto correctos en application.properties.

Esperar `Started MsvUsuarioApplication` y mantener la terminal abierta. Dirección: http://localhost:8004.

Se inicializan los roles. El perfil local dev puede crear un perfil administrador de prueba, pero no hay login ni contraseña de acceso en esta etapa. Usuarios, roles y favoritos funcionan sin token.

## 5. Iniciar Eventos

**Windows con PowerShell**, desde una terminal nueva en la raíz:

```powershell
cd ./msv-evento
.\mvnw.cmd spring-boot:run
```

**Linux/macOS con Bash/Zsh**, desde una terminal nueva en la raíz:

```bash
cd ./msv-evento
sh ./mvnw spring-boot:run
```

Si se usa PowerShell en Linux/macOS, ejecutar `sh ./mvnw spring-boot:run` en lugar de `mvnw.cmd`. JAVA_HOME debe estar configurado en esa terminal según la sección 1. Las URLs locales ya tienen los valores por defecto correctos en application.properties.

Esperar `Started MsvEventoApplication`. Dirección: http://localhost:8005.

Endpoints principales:

- `/api/lugares`: crear y consultar lugares.
- `/api/artistas`: crear y consultar artistas.
- `/api/eventos`: crear y consultar eventos.
- `PATCH /api/eventos/{id}/publicar`: publicar un borrador.
- `PATCH /api/eventos/{id}/cancelar`: cancelar el estado del evento.
- `GET /api/eventos/buscar`: buscar eventos publicados.

El contrato de consulta devuelve `lugar` como objeto anidado. El cliente Feign de SectorAsiento ya está adaptado a ese formato y a `/api/eventos/{id}`.

## 6. Iniciar SectorAsiento y Compras

### SectorAsiento

**Windows con PowerShell**, desde una terminal nueva en la raíz:

```powershell
cd ./msv-SectoerAsiento
.\mvnw.cmd spring-boot:run
```

**Linux/macOS con Bash/Zsh**, desde una terminal nueva en la raíz:

```bash
cd ./msv-SectoerAsiento
sh ./mvnw spring-boot:run
```

Si se usa PowerShell en Linux/macOS, ejecutar `sh ./mvnw spring-boot:run` en lugar de `mvnw.cmd`. JAVA_HOME debe estar configurado en esa terminal según la sección 1. Las URLs locales ya tienen los valores por defecto correctos en application.properties.

Esperar `Started MsvSectoerAsientoApplication`. Dirección: http://localhost:8002.

Puede arrancar sin Eventos, pero configurar inventario, reservar, confirmar y devolver requieren consultar Eventos. Para esas operaciones debe estar iniciado. Compras usa estos endpoints para reservar, confirmar, devolver y cancelar inventario; si se prueba el flujo de compra, también debe estar iniciado.

OpenAPI: http://localhost:8002/openapi.json.

### Compras

Compras usa su base propia en 3311 y necesita Eventos y SectorAsiento iniciados para operar. Si se levantó el Compose de la sección 3, también hay que levantar el de Compras.

**Windows con PowerShell**, desde una terminal nueva en la raíz:

```powershell
cd ./msv-compraEntardaSol
.\mvnw.cmd spring-boot:run
```

**Linux/macOS con Bash/Zsh**, desde una terminal nueva en la raíz:

```bash
cd ./msv-compraEntardaSol
sh ./mvnw spring-boot:run
```

Esperar `Started MsvCompraEntradaSolApplication`. Dirección: http://localhost:8003. OpenAPI: http://localhost:8003/openapi.json.

## 7. Iniciar Gateway

**Windows con PowerShell**, desde una terminal nueva en la raíz:

```powershell
cd ./api-gateway
.\mvnw.cmd spring-boot:run
```

**Linux/macOS con Bash/Zsh**, desde una terminal nueva en la raíz:

```bash
cd ./api-gateway
sh ./mvnw spring-boot:run
```

Si se usa PowerShell en Linux/macOS, ejecutar `sh ./mvnw spring-boot:run` en lugar de `mvnw.cmd`. JAVA_HOME debe estar configurado en esa terminal según la sección 1. Las URLs locales ya tienen los valores por defecto correctos en application.properties.

Esperar `Started ApiGatewayApplication`. Dirección: http://localhost:8080.

El Gateway actualmente enruta:

- `/usuarios`, `/roles` y sus subrutas a Usuarios.
- `/sectores`, `/asientos`, `/reservas` y sus subrutas a SectorAsiento.
- `/eventos/{id}/inventario` y `/eventos/{id}/sectores`, con sus subrutas, a SectorAsiento.
- `/compras`, `/entradas` y `/solicitudes` y sus subrutas, incluida `/compras/eventos/{id}/cancelacion`, a Compras.

Compras se configura con `MSVC_COMPRAS_URL` (por defecto `http://localhost:8003`).

**El catálogo de Eventos todavía no está en el Gateway.** Para `/api/eventos`, `/api/lugares` y `/api/artistas`, usar directamente http://localhost:8005. Agregar MSVC_EVENTOS_URL al Gateway no crea una ruta por sí mismo: falta configurarla en su application.yml.

## 8. Comprobar que responden

En una terminal libre, elegir una alternativa. **PowerShell:**

```powershell
(Invoke-WebRequest -Uri 'http://localhost:8004/roles' -TimeoutSec 10).StatusCode
(Invoke-WebRequest -Uri 'http://localhost:8005/api/eventos' -TimeoutSec 10).StatusCode
(Invoke-WebRequest -Uri 'http://localhost:8002/sectores' -TimeoutSec 10).StatusCode
(Invoke-WebRequest -Uri 'http://localhost:8003/openapi.json' -TimeoutSec 10).StatusCode
(Invoke-WebRequest -Uri 'http://localhost:8080/compras/cliente/1' -TimeoutSec 10).StatusCode
(Invoke-WebRequest -Uri 'http://localhost:8080/sectores' -TimeoutSec 10).StatusCode
```

**Linux/macOS con curl:**

```bash
curl --fail --silent --show-error --output /dev/null --write-out 'Usuarios: %{http_code}\n' http://localhost:8004/roles
curl --fail --silent --show-error --output /dev/null --write-out 'Eventos: %{http_code}\n' http://localhost:8005/api/eventos
curl --fail --silent --show-error --output /dev/null --write-out 'Sectores: %{http_code}\n' http://localhost:8002/sectores
curl --fail --silent --show-error --output /dev/null --write-out 'Compras: %{http_code}\n' http://localhost:8003/openapi.json
curl --fail --silent --show-error --output /dev/null --write-out 'Gateway Compras: %{http_code}\n' http://localhost:8080/compras/cliente/1
curl --fail --silent --show-error --output /dev/null --write-out 'Gateway: %{http_code}\n' http://localhost:8080/sectores
```

Cada consulta debe devolver 200. Una lista vacía es válida. Si no responden, revisar las terminales de Spring y el estado de las bases antes de seguir.

## 9. Probar los flujos completos

Esta sección utiliza **PowerShell 7.3 o superior**, tanto en Windows como en Linux/macOS. Si se estaba usando Bash/Zsh, abrir `pwsh` antes de empezar. Alternativamente, usar Postman como indica la sección 10. Ejecutar los bloques siguientes en **la misma terminal de pruebas** para conservar los IDs en variables. Se crean datos nuevos; no se presupone que los IDs sean 1. Los comandos usan Usuarios, inventario y Compras por Gateway, y el catálogo de Eventos directamente porque falta su ruta en el Gateway.

Los bloques 9.1 a 9.10 prueban el inventario de SectorAsiento. Los bloques 9.11 a 9.13 prueban Compras sobre el mismo evento: compra, pago, QR, devolución con reintegro y cancelación. La cancelación manual de 9.10 debe quedar para el final.

### 9.1 Preparar una función para enviar JSON

```powershell
function Enviar-Json {
    param([string]$Metodo, [string]$Url, $Datos)
    $jsonPrueba = $Datos | ConvertTo-Json -Depth 10
    Invoke-RestMethod -Uri $Url -Method $Metodo -ContentType 'application/json; charset=utf-8' -Body $jsonPrueba
}

$gatewayPrueba = 'http://localhost:8080'
$eventosPrueba = 'http://localhost:8005'
$marcaPrueba = [guid]::NewGuid().ToString('N').Substring(0, 8)
```

### 9.2 Crear cliente y organizador

```powershell
$rolesPrueba = Invoke-RestMethod "$gatewayPrueba/roles"
$rolClientePrueba = $rolesPrueba | Where-Object { $_.name -eq 'CLIENTE' } | Select-Object -First 1
$rolOrganizadorPrueba = $rolesPrueba | Where-Object { $_.name -eq 'ORGANIZADOR' } | Select-Object -First 1

$clientePrueba = Enviar-Json 'POST' "$gatewayPrueba/usuarios" @{
    nombre = 'Cliente prueba'
    email = "cliente-$marcaPrueba@example.com"
    dni = "CLI-$marcaPrueba"
    ciudad = 'Tandil'
    idRol = $rolClientePrueba.id
}

$organizadorPrueba = Enviar-Json 'POST' "$gatewayPrueba/usuarios" @{
    nombre = 'Organizador prueba'
    email = "organizador-$marcaPrueba@example.com"
    dni = "ORG-$marcaPrueba"
    ciudad = 'Tandil'
    idRol = $rolOrganizadorPrueba.id
}
```

Se esperan respuestas 201. No se envía contraseña ni Authorization.

### 9.3 Crear lugar, artista y evento

```powershell
$lugarPrueba = Enviar-Json 'POST' "$eventosPrueba/api/lugares" @{
    nombre = "Teatro prueba $marcaPrueba"
    direccion = 'Calle de prueba 123'
    ciudad = 'Tandil'
    latitud = -37.3288
    longitud = -59.1367
    tipo = 'TEATRO'
}

$artistaPrueba = Enviar-Json 'POST' "$eventosPrueba/api/artistas" @{
    nombre = "Artista prueba $marcaPrueba"
    genero = 'Rock'
}

$eventoPrueba = Enviar-Json 'POST' "$eventosPrueba/api/eventos" @{
    nombre = "Recital prueba $marcaPrueba"
    descripcion = 'Evento para comprobar la integracion'
    tipo = 'RECITAL'
    idOrganizador = $organizadorPrueba.id
    participantesIds = @($artistaPrueba.id)
    fechaHora = [TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTimeOffset]::UtcNow.AddDays(7), 'America/Argentina/Buenos_Aires').ToString('yyyy-MM-ddTHH:mm:ss')
    idLugar = $lugarPrueba.id
    demanda = $false
}
```

El evento se crea en BORRADOR. La fecha se calcula en la zona de Buenos Aires, aunque la computadora esté en otra zona horaria. La fecha enviada es hora local del evento; el inventario utiliza America/Argentina/Buenos_Aires por defecto. Se eligen siete días de anticipación para poder probar devoluciones.

### 9.4 Crear sector y asiento

```powershell
$sectorPrueba = Enviar-Json 'POST' "$gatewayPrueba/sectores" @{
    idLugar = $lugarPrueba.id
    nombre = "Platea prueba $marcaPrueba"
    tipo = 'NUMERADO'
    capacidadTotal = 1
}

$asientoPrueba = Enviar-Json 'POST' "$gatewayPrueba/asientos" @{
    idSector = $sectorPrueba.id
    fila = 'A'
    columna = '1'
}
```

Los asientos se crean antes de asociar el sector a un evento. Después, el servicio impide cambiar esa distribución.

### 9.5 Configurar inventario y publicar

```powershell
Enviar-Json 'PUT' "$gatewayPrueba/eventos/$($eventoPrueba.id)/inventario" @{
    limitePorCliente = 4
}

Enviar-Json 'PUT' "$gatewayPrueba/eventos/$($eventoPrueba.id)/sectores" @{
    idSector = $sectorPrueba.id
    precio = 1500
    capacidadHabilitada = 1
}

Invoke-RestMethod -Uri "$eventosPrueba/api/eventos/$($eventoPrueba.id)/publicar" -Method Patch -Headers @{ 'X-Organizador-Id' = "$($eventoPrueba.idOrganizador)" }

$urlDisponibilidadPrueba = "$gatewayPrueba/eventos/$($eventoPrueba.id)/sectores/$($sectorPrueba.id)/disponibilidad"
Invoke-RestMethod $urlDisponibilidadPrueba
```

Se esperan 204 al inicializar el inventario y 200 al configurar, publicar y consultar. Antes de publicar, Eventos comprueba que el nombre, descripción, tipo, organizador, fecha futura, lugar, coordenadas y participantes estén completos y que SectorAsiento tenga al menos un sector con precio no negativo y capacidad positiva. Si falta algo, la solicitud falla con un mensaje indicando qué completar y el evento permanece en BORRADOR. La disponibilidad debe indicar un lugar disponible.

### 9.6 Reservar y repetir la misma solicitud

```powershell
$pedidoReservaPrueba = @{
    claveIdempotencia = "reserva-$marcaPrueba"
    idCliente = $clientePrueba.id
    idEvento = $eventoPrueba.id
    idSector = $sectorPrueba.id
    cantidad = 1
    idAsientos = @($asientoPrueba.id)
}

$reservaPrueba = Enviar-Json 'POST' "$gatewayPrueba/reservas" $pedidoReservaPrueba
$reservaRepetidaPrueba = Enviar-Json 'POST' "$gatewayPrueba/reservas" $pedidoReservaPrueba
$reservaPrueba
$reservaRepetidaPrueba
```

Ambas solicitudes devuelven 200 y el mismo ID. El estado debe ser ACTIVA. La reserva dura diez minutos. Usar otra clave para intentar reservar el mismo asiento mientras sigue ocupado debe devolver 409.

### 9.7 Simular pago aprobado

```powershell
Invoke-RestMethod -Uri "$gatewayPrueba/reservas/$($reservaPrueba.id)/confirmacion" -Method Post
Invoke-RestMethod $urlDisponibilidadPrueba
```

El estado debe ser CONFIRMADA y la disponibilidad debe mostrar vendidos=1, reservados=0 y disponibles=0. Repetir la confirmación no vende una segunda vez.

Si pasó el plazo, la confirmación puede devolver 200 con estado VENCIDA: no equivale a una venta. En el flujo de Compras, un pago sobre una reserva vencida deja la compra `EXPIRADA` y reintegra el importe simulado. Esta prueba manual no cobra dinero ni emite QR.

### 9.8 Probar una devolución aprobada

```powershell
$pedidoDevolucionPrueba = @{
    claveIdempotencia = "devolucion-$marcaPrueba"
    cantidad = 1
    solicitadaEn = (Get-Date).ToUniversalTime().ToString('o')
    idAsientos = @($asientoPrueba.id)
}

Enviar-Json 'POST' "$gatewayPrueba/reservas/$($reservaPrueba.id)/devoluciones" $pedidoDevolucionPrueba
Enviar-Json 'POST' "$gatewayPrueba/reservas/$($reservaPrueba.id)/devoluciones" $pedidoDevolucionPrueba
Invoke-RestMethod $urlDisponibilidadPrueba
```

La reserva debe quedar DEVUELTA y el lugar disponible. El reintento utiliza exactamente el mismo contenido, incluida solicitadaEn. Esa fecha representa la solicitud original que informará Compras; debe respetar las 72 horas previas al evento. No se reintegra dinero desde SectorAsiento.

### 9.9 Alternativas: rechazo, vencimiento y campo

- Para simular pago rechazado, crear una nueva reserva con otra clave y llamar a `POST /reservas/{id}/liberacion` antes de confirmarla. Debe quedar LIBERADA; repetir es seguro.
- Para probar vencimiento, crear una nueva reserva y esperar diez minutos. Consultar `GET /reservas/{id}`: debe quedar VENCIDA y liberar el lugar. También hay un barrido automático cada 30 segundos.
- Para CAMPO, crear otro sector de tipo CAMPO con la capacidad deseada, sin crear Asientos. Configurarlo para el evento con su precio/capacidad. Reservar con `idAsientos: []` y la cantidad deseada. La cantidad no puede superar el cupo ni el límite por cliente.

No reutilizar una clave de idempotencia para una solicitud diferente: devuelve 409.

### 9.10 Cancelación del evento completo

La cancelación completa se realiza con una sola llamada al endpoint de Compras, documentado en 9.13. Ejecutarla al final de las pruebas porque bloquea nuevas ventas, cancela las compras pendientes, reintegra las entradas y cierra el inventario. No hace falta llamar por separado a Eventos y SectorAsiento.

### 9.11 Compra, pago y uso del QR

```powershell
$comprasPrueba = 'http://localhost:8003'

$pedidoCompraPrueba = @{
    claveIdempotencia = "compra-$marcaPrueba"
    idCliente = $clientePrueba.id
    idEvento = $eventoPrueba.id
    idSector = $sectorPrueba.id
    idsAsientos = @($asientoPrueba.id)
    medioDePago = 'TARJETA'
}

$compraPrueba = Enviar-Json 'POST' "$comprasPrueba/compras" $pedidoCompraPrueba
$compraRepetidaPrueba = Enviar-Json 'POST' "$comprasPrueba/compras" $pedidoCompraPrueba
$compraPrueba
$compraRepetidaPrueba

$compraPagadaPrueba = Invoke-RestMethod -Uri "$comprasPrueba/compras/$($compraPrueba.id)/pago?aprobado=true" -Method Put
$compraPagadaPrueba

$entradasPrueba = Invoke-RestMethod "$comprasPrueba/entradas/compra/$($compraPrueba.id)"
$entradaPrueba = $entradasPrueba | Select-Object -First 1
$entradaPrueba

Invoke-RestMethod "$comprasPrueba/entradas/qr/$($entradaPrueba.qr)"
Invoke-RestMethod -Uri "$comprasPrueba/entradas/$($entradaPrueba.id)/uso" -Method Patch
Invoke-RestMethod $urlDisponibilidadPrueba
```

Las dos compras devuelven 201, el mismo `id` y estado `PENDIENTE`: la clave de idempotencia evita duplicados. El pago deja la compra `APROBADA` con `referenciaPago` con prefijo `SIM-`. La entrada nace `NO_ESCANEADO` y el `PATCH` la pasa a `ESCANEADO`; repetir ese `PATCH` devuelve 409. La disponibilidad debe mostrar vendidos=1, reservados=0 y disponibles=0.

Una entrada ya escaneada no se puede devolver. Por eso 9.12 crea otro sector y otra compra en lugar de reutilizar esta entrada.

### 9.12 Devolución con reintegro

```powershell
$sectorDevolucionPrueba = Enviar-Json 'POST' "$gatewayPrueba/sectores" @{
    idLugar = $lugarPrueba.id
    nombre = "Platea devolucion $marcaPrueba"
    tipo = 'NUMERADO'
    capacidadTotal = 1
}

$asientoDevolucionPrueba = Enviar-Json 'POST' "$gatewayPrueba/asientos" @{
    idSector = $sectorDevolucionPrueba.id
    fila = 'B'
    columna = '1'
}

Enviar-Json 'PUT' "$gatewayPrueba/eventos/$($eventoPrueba.id)/sectores" @{
    idSector = $sectorDevolucionPrueba.id
    precio = 1500
    capacidadHabilitada = 1
}

$compraDevolucionPrueba = Enviar-Json 'POST' "$comprasPrueba/compras" @{
    claveIdempotencia = "compra-devolucion-$marcaPrueba"
    idCliente = $clientePrueba.id
    idEvento = $eventoPrueba.id
    idSector = $sectorDevolucionPrueba.id
    idsAsientos = @($asientoDevolucionPrueba.id)
    medioDePago = 'EFECTIVO'
}

$compraDevolucionPrueba = Invoke-RestMethod -Uri "$comprasPrueba/compras/$($compraDevolucionPrueba.id)/pago?aprobado=true" -Method Put
$entradaDevolucionPrueba = (Invoke-RestMethod "$comprasPrueba/entradas/compra/$($compraDevolucionPrueba.id)") | Select-Object -First 1

$solicitudPrueba = Enviar-Json 'POST' "$comprasPrueba/solicitudes" @{
    idEntrada = $entradaDevolucionPrueba.id
    idCliente = $clientePrueba.id
    descripcion = 'No puedo asistir'
}

$solicitudPrueba
Invoke-RestMethod "$comprasPrueba/solicitudes/pendientes"

$solicitudResueltaPrueba = Enviar-Json 'PUT' "$comprasPrueba/solicitudes/$($solicitudPrueba.id)/resolver" @{
    idAdministrador = 1
    estadoSolicitud = 'ACEPTADA'
}
$solicitudResueltaPrueba

Invoke-RestMethod "$comprasPrueba/entradas/$($entradaDevolucionPrueba.id)"
```

La solicitud nace `PENDIENTE` y aparece en el listado de pendientes. Al resolverla como `ACEPTADA`, la respuesta trae `estadoReintegro = COMPLETADO` y `referenciaReintegro` con prefijo `SIM-REF-`; la entrada queda `DEVOLUCION_ACEPTADA` y el asiento se libera en SectorAsiento. Repetir la resolución con el mismo estado no genera un segundo reintegro; resolverla con otro estado devuelve 409. Si faltan menos de 72 horas para el evento, el `POST /solicitudes` devuelve 409.

### 9.13 Cancelación del evento desde Compras

```powershell
$cancelacionPrueba = Invoke-RestMethod -Uri "$comprasPrueba/compras/eventos/$($eventoPrueba.id)/cancelacion" -Method Post
$cancelacionPrueba
$cancelacionRepetidaPrueba = Invoke-RestMethod -Uri "$comprasPrueba/compras/eventos/$($eventoPrueba.id)/cancelacion" -Method Post
$cancelacionRepetidaPrueba
Invoke-RestMethod "$comprasPrueba/compras/$($compraPrueba.id)"
```

La primera llamada debe devolver `comprasCanceladas` y `entradasReintegradas` mayores o iguales a 1 e `inventarioCancelado = true`. También deja el evento `CANCELADO` en el catálogo. La compra de 9.11 queda `CANCELADA` con `estadoReintegro = COMPLETADO` y su entrada `EVENTO_CANCELADO`. La segunda llamada es idempotente: los contadores vuelven a cero y no cambia la referencia de reintegro. Si SectorAsiento responde 404 porque el evento no tenía inventario, `inventarioCancelado` es `false` y la operación igual termina bien.

## 10. Alternativa con Postman e IntelliJ

Para Postman hay dos colecciones. La de SectorAsiento, `msv-SectoerAsiento/TicketYa-SectorAsiento.postman_collection.json`, cubre lugar, evento, inventario, reservas y devoluciones: crear antes el evento/lugar en Eventos, configurar idEvento y usar los IDs reales. La de Compras, `msv-compraEntardaSol/TicketYa-Compras.postman_collection.json`, cubre compras, entradas y solicitudes; el detalle paso a paso está en `msv-compraEntardaSol/docs/PRUEBAS-POSTMAN.md`. Ambas colecciones capturan IDs en variables. Cambiar las claves para nuevas ejecuciones y limpiar solicitadaEn antes de iniciar una nueva devolución.

Para IntelliJ, importar cada proyecto desde su pom.xml, sincronizar Maven y seleccionar un SDK Java 21 o superior. Iniciar las bases y después ejecutar:

| Proyecto | Clase principal |
| --- | --- |
| msv-usuario | com.msvusuario.MsvUsuarioApplication |
| msv-evento | msvevento.MsvEventoApplication |
| msv-SectoerAsiento | com.universidad.msvsectoerasiento.MsvSectoerAsientoApplication |
| msv-compraEntardaSol | org.msvcompraentradasol.MsvCompraEntradaSolApplication |
| api-gateway | com.apigateway.ApiGatewayApplication |

Usar la carpeta del microservicio como Working directory. Definir las mismas variables de entorno que muestran los comandos de arranque cuando se quieran personalizar valores. Iniciar cada aplicación una sola vez: no ejecutar a la vez la misma desde IntelliJ y PowerShell.

## 11. Pruebas automáticas y estado verificado

Desde una terminal en la raíz, con el JDK configurado. Cada bloque regresa a la raíz después de probar un módulo.

**Windows con PowerShell:**

```powershell
Push-Location ./msv-usuario
.\mvnw.cmd test
Pop-Location
Push-Location ./msv-SectoerAsiento
.\mvnw.cmd test
Pop-Location
Push-Location ./api-gateway
.\mvnw.cmd test
Pop-Location
Push-Location ./msv-evento
.\mvnw.cmd test
Pop-Location
Push-Location ./msv-compraEntardaSol
.\mvnw.cmd test
Pop-Location
```

**Linux/macOS con Bash/Zsh:**

```bash
(cd ./msv-usuario && sh ./mvnw test)
(cd ./msv-SectoerAsiento && sh ./mvnw test)
(cd ./api-gateway && sh ./mvnw test)
(cd ./msv-evento && sh ./mvnw test)
(cd ./msv-compraEntardaSol && sh ./mvnw test)
```

No ejecutar simultáneamente las pruebas de Eventos y la aplicación Eventos sobre la misma base de desarrollo.

Usuarios, SectorAsiento, Compras y Gateway utilizan H2 o proveedores HTTP de prueba y no necesitan servicios externos para sus suites. **Eventos actualmente solo tiene una prueba de arranque y usa el esquema eventos del MySQL compartido en 3306**, por lo que esa base debe estar iniciada. Sus pruebas no están aisladas con un perfil H2; con ddl-auto=update, el arranque puede actualizar el esquema configurado.

Última verificación: 15 pruebas de Usuarios, 37 de SectorAsiento, 61 de Compras y 10 de Gateway pasaron. Eventos compiló, pero su prueba de arranque falló por conexión a MySQL: en esa revisión solo estaba levantada la base de Usuarios. Eso no prueba un error de compilación ni permite certificar el flujo integrado real.

Pendientes conocidos: rutas del catálogo de Eventos en Gateway, coordinación automática entre la cancelación de Eventos y Compras, notificaciones a compradores afectados y validaciones/pruebas de negocio de Eventos. El escenario de la sección 9 se ejecutó completo el 10 de noviembre de 2026 contra MySQL real (Compras en 3311, Eventos en 8005 y SectorAsiento en 8002): compra, pago, uso de QR, devolución con reintegro, cancelación desde Compras y consulta por Gateway, sin fallos.

Para pruebas específicas con MySQL descartable, consultar README.md de SectorAsiento. Su compose.mysql-test.yml usa 3309 y no es la base de desarrollo de esta guía.

## 12. Problemas frecuentes

| Síntoma | Qué revisar |
| --- | --- |
| Docker no se conecta al motor | Docker Desktop iniciado en Windows/macOS o Docker Engine disponible en Linux; docker version debe mostrar Server. |
| JAVA_HOME inválido | JDK 21+ completo, java/javac disponibles y JAVA_HOME configurado en esa misma terminal según la sección 1. |
| Communications link failure / no puede determinar el dialecto | Base correspondiente iniciada, puerto y credenciales correctos. No arreglarlo solo forzando un dialecto. |
| Access denied de MySQL | Credenciales de aplicación y del volumen existente; no cambian al editar variables. |
| Puerto ocupado | Detener la instancia anterior con Ctrl+C o Stop en IntelliJ. |
| Inventario devuelve 503 | Eventos disponible en 8005 y MSVC_EVENTOS_URL correcta. |
| Evento devuelve 404 desde Feign | Comprobar GET http://localhost:8005/api/eventos/{id}. |
| /api/eventos devuelve 404 por Gateway | Falta la ruta del catálogo; usar directamente 8005 por ahora. |
| Reserva devuelve 409 | Publicación/fecha del evento, cupos, asientos, límite por cliente y clave de idempotencia. |
| Inventario devuelve 502 | Revisar el contrato del proveedor: evento con lugar.id y fechaHora. |
| Compras devuelve 503 | Eventos en 8005 y SectorAsiento en 8002 iniciados; MSVC_EVENTOS_URL y MSVC_SECTORES_URL correctas. |
| Compra devuelve 409 | Evento publicado y futuro, clave de idempotencia reutilizada con otro contenido o asiento ya ocupado. |
| Pago devuelve 409 | La compra ya se procesó con otro resultado (aprobado/rechazado). |
| Devolución devuelve 409 | Faltan menos de 72 horas, ya existe una solicitud pendiente/aceptada o la entrada no está PAGA. |
| No se puede devolver una entrada | El QR ya está ESCANEADO; usar otra compra para probar la devolución. |
| Reventa falla por índice único | Ejecutar una vez `ALTER TABLE entrada DROP INDEX uk_entrada_evento_asiento;` sobre `compra_entrada_sol`. |
| Compras no conecta a su base | Levantar `docker compose -f ./msv-compraEntardaSol/compose.mysql.yml up -d --wait` y revisar el puerto 3311. |
| Ya se canceló el evento, pero el inventario sigue abierto | Ejecutar la segunda llamada de 9.10 o `POST /compras/eventos/{id}/cancelacion` (9.13). |

Ver logs de bases desde la raíz:

```sh
docker compose logs --tail 50 mysql
docker compose -f ./msv-compraEntardaSol/compose.mysql.yml logs --tail 50
```

### Ajustar variables sin rutas personales

Los valores por defecto alcanzan cuando todo corre en la misma computadora. Para personalizarlos, definir variables en la terminal del microservicio antes de iniciarlo, o en su configuración de IntelliJ.

**Ejemplo PowerShell (Usuarios):**

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/usuarios'
$env:MSV_EVENTO_URL='http://localhost:8005'
```

**Ejemplo Bash/Zsh (Usuarios):**

```bash
export DB_URL='jdbc:mysql://localhost:3306/usuarios'
export MSV_EVENTO_URL='http://localhost:8005'
```

Para otras aplicaciones cambiar el esquema de DB_URL. Las variables de URLs son MSV_EVENTO_URL en Usuarios; MSVC_EVENTOS_URL y MSVC_COMPRAS_URL en SectorAsiento; MSVC_EVENTOS_URL y MSVC_SECTORES_URL en Compras; MSVC_USUARIOS_URL y MSVC_SECTORES_URL en Gateway. Compras usa además DB_URL, DB_USERNAME y DB_PASSWORD sobre el puerto 3311. Los puertos HTTP por defecto siguen siendo 8004, 8005, 8002, 8003 y 8080. Si una variable vieja apunta a otro puerto, corregirla o quitarla.

Si los microservicios también se ejecutan en contenedores, localhost representa a cada contenedor: se requieren URLs con los nombres de servicio de su red Docker. Esta guía describe Java ejecutándose en el equipo y solo MySQL en Docker, no un despliegue completo de las aplicaciones en contenedores.

## 13. Detener

Detener las aplicaciones con Ctrl+C en sus terminales o Stop en IntelliJ, en cualquiera de los sistemas. Después, desde la raíz:

```sh
docker compose stop
docker compose -f ./msv-compraEntardaSol/compose.mysql.yml stop
```

Estos comandos conservan los datos de los volúmenes. Para volver a iniciar las bases, repetir los comandos up de la sección 3 y el de la base propia de Compras.

## Nota de verificación del cambio de bases

Se validaron la configuración Compose, la compilación y las suites automáticas (61 pruebas de Compras y 10 del Gateway, entre otras). También se ejecutó el flujo E2E de Compras contra MySQL 3311, Eventos 8005 y SectorAsiento 8002, y se comprobó el enrutamiento de `/compras` por el Gateway. La migración de volúmenes anteriores no se ejecutó durante este cambio; los datos previos no fueron modificados.

## Portabilidad de la guía

No hay rutas absolutas de usuarios ni versiones de JDK ligadas a una computadora. Los comandos se basan en la raíz del repositorio. Se revisó la sintaxis de los ejemplos PowerShell y Bash; eso no equivale a ejecutar toda la aplicación en cada sistema operativo. La prueba completa de las secciones 8 y 9 debe realizarse en el equipo de destino con Docker y los servicios iniciados.
