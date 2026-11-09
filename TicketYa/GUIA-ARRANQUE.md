# Guía de arranque y prueba de TicketYa

Actualizada al 8 de noviembre de 2026, después de incorporar Eventos desde main y separar JWT en la carpeta `futura jwt`.

Usuarios, Eventos, SectorAsiento y Gateway tienen un proyecto Spring Boot ejecutable. Las bases se levantan con Docker; las aplicaciones, desde PowerShell o IntelliJ. No hay un Compose único para todo el sistema. Compras todavía no está operativo: para probar inventario se simulan sus llamadas desde Postman o PowerShell.

## 1. Requisitos

- Docker Desktop iniciado, con el motor de contenedores Linux disponible.
- JDK 21 o superior. En este equipo está instalado `C:/Users/franc/.jdks/openjdk-26.0.2`.
- Una terminal libre para Docker y pruebas, y cuatro terminales para las aplicaciones Java.
- Conexión a internet si Maven necesita descargar dependencias. Cada proyecto incluye Maven Wrapper.

Desde PowerShell, comprobar Docker:

```powershell
docker version
```

Debe aparecer la sección Server. Si no aparece, abrir Docker Desktop y esperar a que el motor esté listo.

Los bloques de arranque de esta guía incluyen la ruta completa para poder copiarlos en terminales nuevas. La variable JAVA_HOME se define solo para esa terminal, sin cambiar la configuración permanente del equipo.

## 2. Puertos actuales

| Componente | Puerto local | Base / destino |
| --- | --- | --- |
| MySQL Usuarios | 3307 | usuarios |
| MySQL Eventos | 3308 | eventos |
| MySQL SectorAsiento | 3310 | sectores_asientos |
| Usuarios | 8004 | MySQL en 3307 |
| Eventos | 8005 | MySQL en 3308 |
| SectorAsiento | 8002 | MySQL en 3310; Feign a Eventos en 8005 |
| Gateway | 8080 | Usuarios en 8004 y SectorAsiento en 8002 |
| Compras | 8003 provisional | Todavía pendiente |

Orden recomendado: **Docker → las tres bases → Usuarios y Eventos → SectorAsiento → Gateway → pruebas**.

No se requiere JWT. `/auth/login` y `/auth/registro` están retirados; los perfiles se crean con `POST /usuarios`. La carpeta `futura jwt` conserva la implementación para otra etapa.

## 3. Levantar las tres bases

En la terminal de Docker:

```powershell
Set-Location 'C:/Users/franc/OneDrive/Escritorio/RefactorArqui/ArquitecturasWeb/TicketYa'

docker compose -f ./msv-usuario/docker-compose.yml up -d --wait
docker compose -f ./msv-evento/docker-compose.yaml up -d --wait
docker compose -f ./msv-SectoerAsiento/compose.mysql.yml up -d --wait
```

Comprobar que estén saludables:

```powershell
docker compose -f ./msv-usuario/docker-compose.yml ps
docker compose -f ./msv-evento/docker-compose.yaml ps
docker compose -f ./msv-SectoerAsiento/compose.mysql.yml ps
```

| Base | Usuario local | Contraseña local |
| --- | --- | --- |
| usuarios | usuario_app | usuario_dev |
| eventos | evento_app | evento_dev |
| sectores_asientos | sector_app | sector_dev |

Estos valores coinciden con las configuraciones de desarrollo. Si hay variables DB_URL, DB_USERNAME, DB_PASSWORD o MYSQL_ROOT_PASSWORD definidas previamente, revisar que no reemplacen los valores de la guía. DB_PASSWORD debe coincidir entre el Compose de cada base y su aplicación. Cambiar una variable no cambia las credenciales de un volumen ya inicializado.

No usar el puerto 3308 para SectorAsiento: ahora pertenece a Eventos. SectorAsiento usa **3310**. No confundir `msv-evento`, el proyecto ejecutable, con la carpeta `msv-eventos`.

## 4. Iniciar Usuarios

En una terminal nueva:

```powershell
Set-Location 'C:/Users/franc/OneDrive/Escritorio/RefactorArqui/ArquitecturasWeb/TicketYa/msv-usuario'
$env:JAVA_HOME='C:/Users/franc/.jdks/openjdk-26.0.2'
$env:MSV_EVENTO_URL='http://localhost:8005'
.\mvnw.cmd spring-boot:run
```

Esperar `Started MsvUsuarioApplication` y mantener la terminal abierta. Dirección: http://localhost:8004.

Se inicializan los roles. El perfil local dev puede crear un perfil administrador de prueba, pero no hay login ni contraseña de acceso en esta etapa. Usuarios, roles y favoritos funcionan sin token.

## 5. Iniciar Eventos

En otra terminal:

```powershell
Set-Location 'C:/Users/franc/OneDrive/Escritorio/RefactorArqui/ArquitecturasWeb/TicketYa/msv-evento'
$env:JAVA_HOME='C:/Users/franc/.jdks/openjdk-26.0.2'
.\mvnw.cmd spring-boot:run
```

Esperar `Started MsvEventoApplication`. Dirección: http://localhost:8005.

Endpoints principales:

- `/api/lugares`: crear y consultar lugares.
- `/api/artistas`: crear y consultar artistas.
- `/api/eventos`: crear y consultar eventos.
- `PATCH /api/eventos/{id}/publicar`: publicar un borrador.
- `PATCH /api/eventos/{id}/cancelar`: cancelar el estado del evento.
- `GET /api/eventos/buscar`: buscar eventos publicados.

El contrato de consulta devuelve `lugar` como objeto anidado. El cliente Feign de SectorAsiento ya está adaptado a ese formato y a `/api/eventos/{id}`.

## 6. Iniciar SectorAsiento

En otra terminal:

```powershell
Set-Location 'C:/Users/franc/OneDrive/Escritorio/RefactorArqui/ArquitecturasWeb/TicketYa/msv-SectoerAsiento'
$env:JAVA_HOME='C:/Users/franc/.jdks/openjdk-26.0.2'
$env:MSVC_EVENTOS_URL='http://localhost:8005'
$env:MSVC_COMPRAS_URL='http://localhost:8003'
.\mvnw.cmd spring-boot:run
```

Esperar `Started MsvSectoerAsientoApplication`. Dirección: http://localhost:8002.

Puede arrancar sin Eventos, pero configurar inventario, reservar, confirmar y devolver requieren consultar Eventos. Para esas operaciones debe estar iniciado. Compras no hace falta para ejecutar las llamadas manuales de esta guía.

OpenAPI: http://localhost:8002/openapi.json.

## 7. Iniciar Gateway

En otra terminal:

```powershell
Set-Location 'C:/Users/franc/OneDrive/Escritorio/RefactorArqui/ArquitecturasWeb/TicketYa/api-gateway'
$env:JAVA_HOME='C:/Users/franc/.jdks/openjdk-26.0.2'
$env:MSVC_USUARIOS_URL='http://localhost:8004'
$env:MSVC_SECTORES_URL='http://localhost:8002'
.\mvnw.cmd spring-boot:run
```

Esperar `Started ApiGatewayApplication`. Dirección: http://localhost:8080.

El Gateway actualmente enruta:

- `/usuarios`, `/roles` y sus subrutas a Usuarios.
- `/sectores`, `/asientos`, `/reservas` y sus subrutas a SectorAsiento.
- `/eventos/{id}/inventario` y `/eventos/{id}/sectores`, con sus subrutas, a SectorAsiento.

**El catálogo de Eventos todavía no está en el Gateway.** Para `/api/eventos`, `/api/lugares` y `/api/artistas`, usar directamente http://localhost:8005. Agregar MSVC_EVENTOS_URL al Gateway no crea una ruta por sí mismo: falta configurarla en su application.yml.

## 8. Comprobar que responden

En una terminal libre:

```powershell
(Invoke-WebRequest -Uri 'http://localhost:8004/roles' -TimeoutSec 10).StatusCode
(Invoke-WebRequest -Uri 'http://localhost:8005/api/eventos' -TimeoutSec 10).StatusCode
(Invoke-WebRequest -Uri 'http://localhost:8002/sectores' -TimeoutSec 10).StatusCode
(Invoke-WebRequest -Uri 'http://localhost:8080/sectores' -TimeoutSec 10).StatusCode
```

Cada consulta debe devolver 200. Una lista vacía es válida. Si no responden, revisar las terminales de Spring y el estado de las bases antes de seguir.

## 9. Probar un flujo completo de inventario

Ejecutar los bloques siguientes en **la misma terminal de pruebas** para conservar los IDs en variables. Se crean datos nuevos; no se presupone que los IDs sean 1. Los comandos usan Usuarios e inventario por Gateway, y el catálogo de Eventos directamente porque falta su ruta en el Gateway.

### 9.1 Preparar una función para enviar JSON

```powershell
function Enviar-Json {
    param([string]$Metodo, [string]$Url, $Datos)
    $jsonPrueba = $Datos | ConvertTo-Json -Depth 10
    Invoke-RestMethod -Uri $Url -Method $Metodo -ContentType 'application/json' -Body $jsonPrueba
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
    fechaHora = (Get-Date).AddDays(7).ToString('yyyy-MM-ddTHH:mm:ss')
    idLugar = $lugarPrueba.id
    demanda = $false
}
```

El evento se crea en BORRADOR. La fecha enviada es hora local del evento; el inventario utiliza America/Argentina/Buenos_Aires por defecto. Se eligen siete días de anticipación para poder probar devoluciones.

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

Invoke-RestMethod -Uri "$eventosPrueba/api/eventos/$($eventoPrueba.id)/publicar" -Method Patch

$urlDisponibilidadPrueba = "$gatewayPrueba/eventos/$($eventoPrueba.id)/sectores/$($sectorPrueba.id)/disponibilidad"
Invoke-RestMethod $urlDisponibilidadPrueba
```

Se esperan 204 al inicializar el inventario y 200 al configurar, publicar y consultar. La disponibilidad debe indicar un lugar disponible.

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

Si pasó el plazo, la confirmación puede devolver 200 con estado VENCIDA: no equivale a una venta. Compras debería compensar un pago tardío. Esta prueba manual no cobra dinero ni emite QR.

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

### 9.10 Cancelación: todavía requiere dos llamadas

```powershell
Invoke-RestMethod -Uri "$eventosPrueba/api/eventos/$($eventoPrueba.id)/cancelar" -Method Patch
Invoke-RestMethod -Uri "$gatewayPrueba/eventos/$($eventoPrueba.id)/inventario/cancelacion" -Method Post
Invoke-RestMethod $urlDisponibilidadPrueba
```

Hacer esto al terminar la prueba: el evento ya no admitirá nuevas reservas. La primera llamada cancela el estado de Eventos; la segunda libera inventario y marca cancelado=true. **Eventos todavía no invoca automáticamente la segunda operación.** La cancelación del inventario se puede repetir; el endpoint de cancelación de Eventos devuelve 409 si ya estaba cancelado.

## 10. Alternativa con Postman e IntelliJ

Para Postman, importar `msv-SectoerAsiento/TicketYa-SectorAsiento.postman_collection.json`. Crear antes el evento/lugar en Eventos, configurar idEvento y usar los IDs reales. La colección captura IDs de sector, asiento y reserva. Cambiar las claves para nuevas ejecuciones y limpiar solicitadaEn antes de iniciar una nueva devolución.

Para IntelliJ, importar cada proyecto desde su pom.xml, sincronizar Maven y seleccionar un SDK Java 21 o superior. Iniciar las bases y después ejecutar:

| Proyecto | Clase principal |
| --- | --- |
| msv-usuario | com.msvusuario.MsvUsuarioApplication |
| msv-evento | msvevento.MsvEventoApplication |
| msv-SectoerAsiento | com.universidad.msvsectoerasiento.MsvSectoerAsientoApplication |
| api-gateway | com.apigateway.ApiGatewayApplication |

Usar la carpeta del microservicio como Working directory. Definir las mismas variables de entorno que muestran los comandos de arranque cuando se quieran personalizar valores. Iniciar cada aplicación una sola vez: no ejecutar a la vez la misma desde IntelliJ y PowerShell.

## 11. Pruebas automáticas y estado verificado

Desde la raíz, con JAVA_HOME definido:

```powershell
Set-Location 'C:/Users/franc/OneDrive/Escritorio/RefactorArqui/ArquitecturasWeb/TicketYa'
$env:JAVA_HOME='C:/Users/franc/.jdks/openjdk-26.0.2'

.\msv-usuario\mvnw.cmd -f ./msv-usuario/pom.xml test
.\msv-SectoerAsiento\mvnw.cmd -f ./msv-SectoerAsiento/pom.xml test
.\api-gateway\mvnw.cmd -f ./api-gateway/pom.xml test
.\msv-evento\mvnw.cmd -f ./msv-evento/pom.xml test
```

Si un Wrapper no admite ser invocado desde otra carpeta, abrir una terminal en el proyecto y ejecutar `.\mvnw.cmd test`.

Usuarios, SectorAsiento y Gateway utilizan H2 o proveedores HTTP de prueba y no necesitan servicios externos para sus suites. **Eventos actualmente solo tiene una prueba de arranque y usa su MySQL configurado en 3308**, por lo que esa base debe estar iniciada. Sus pruebas no están aisladas con un perfil H2; con ddl-auto=update, el arranque puede actualizar el esquema configurado.

Última verificación: 15 pruebas de Usuarios y 37 de SectorAsiento pasaron. Eventos compiló, pero su prueba de arranque falló por conexión a MySQL: en esa revisión solo estaba levantada la base de Usuarios. Eso no prueba un error de compilación ni permite certificar el flujo integrado real. El Gateway tenía seis pruebas correctas de la verificación anterior.

Pendientes conocidos: rutas del catálogo de Eventos en Gateway, coordinación automática de cancelación e inventario, y validaciones/pruebas de negocio de Eventos. El escenario de la sección 9 es la comprobación manual que debe realizarse con los servicios y bases reales. No se ejecutó ese escenario como parte de la actualización de esta guía.

Para pruebas específicas con MySQL descartable, consultar README.md de SectorAsiento. Su compose.mysql-test.yml usa 3309 y no es la base de desarrollo de esta guía.

## 12. Problemas frecuentes

| Síntoma | Qué revisar |
| --- | --- |
| Docker no se conecta al motor | Docker Desktop abierto y sección Server en docker version. |
| JAVA_HOME inválido | Ruta del JDK y variable definida en esa misma terminal. |
| Communications link failure / no puede determinar el dialecto | Base correspondiente iniciada, puerto y credenciales correctos. No arreglarlo solo forzando un dialecto. |
| Access denied de MySQL | Credenciales de aplicación y del volumen existente; no cambian al editar variables. |
| Puerto ocupado | Detener la instancia anterior con Ctrl+C o Stop en IntelliJ. |
| Inventario devuelve 503 | Eventos disponible en 8005 y MSVC_EVENTOS_URL correcta. |
| Evento devuelve 404 desde Feign | Comprobar GET http://localhost:8005/api/eventos/{id}. |
| /api/eventos devuelve 404 por Gateway | Falta la ruta del catálogo; usar directamente 8005 por ahora. |
| Reserva devuelve 409 | Publicación/fecha del evento, cupos, asientos, límite por cliente y clave de idempotencia. |
| Inventario devuelve 502 | Revisar el contrato del proveedor: evento con lugar.id y fechaHora. |
| Ya se canceló el evento, pero el inventario sigue abierto | Ejecutar la segunda llamada de cancelación indicada en 9.10. |

Ver logs de bases desde la raíz:

```powershell
docker compose -f ./msv-usuario/docker-compose.yml logs --tail 50 mysql-usuario
docker compose -f ./msv-evento/docker-compose.yaml logs --tail 50 mysql-evento
docker compose -f ./msv-SectoerAsiento/compose.mysql.yml logs --tail 50 mysql-sector
```

## 13. Detener

Detener las aplicaciones con Ctrl+C en sus terminales o Stop en IntelliJ. Después, desde la raíz:

```powershell
docker compose -f ./msv-usuario/docker-compose.yml stop
docker compose -f ./msv-evento/docker-compose.yaml stop
docker compose -f ./msv-SectoerAsiento/compose.mysql.yml stop
```

Estos comandos conservan los datos de los volúmenes. Para volver a iniciar las bases, repetir los comandos up de la sección 3.
