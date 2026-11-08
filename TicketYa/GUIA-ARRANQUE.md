# Guia para levantar TicketYa 

Estado revisado del proyecto: Usuarios, SectorAsiento y API Gateway tienen configuracion para ejecutarse. Eventos y Compras todavia requieren completar sus proyectos. Esta guia describe el arranque local con las bases en Docker y las aplicaciones Spring Boot en PowerShell o IntelliJ; no hay aun un Compose que levante todo el sistema.

## 1. Requisitos y carpeta de trabajo

- Docker Desktop iniciado y con el motor de contenedores Linux funcionando.
- JDK 21 o superior. En este equipo existe `C:/Users/franc/.jdks/openjdk-26.0.2`.
- Maven Wrapper incluido en cada proyecto ejecutable. No hace falta instalar Maven por separado; la primera ejecucion puede descargar dependencias.
- Una terminal para las bases y una terminal independiente por cada aplicacion Spring Boot.

Abrir PowerShell en la carpeta raiz TicketYa. En las terminales nuevas, volver a esta carpeta antes de copiar los comandos de cada seccion:

```powershell
Set-Location 'C:/Users/franc/OneDrive/Escritorio/RefactorArqui/ArquitecturasWeb/TicketYa'
```

Comprobar Docker:

```powershell
docker version
```

Tiene que aparecer tambien la seccion Server. Si dice que no puede conectarse al motor, abrir Docker Desktop y esperar a que este listo.

Para usar el JDK de este equipo, ejecutar esto en **cada terminal donde se vaya a iniciar Java**:

```powershell
$env:JAVA_HOME='C:/Users/franc/.jdks/openjdk-26.0.2'
& "$env:JAVA_HOME/bin/java.exe" -version
```

No hace falta cambiar variables permanentes del equipo. Si se usa otro JDK instalado, reemplazar la ruta por su carpeta raiz.

## 2. Puertos y dependencias

| Componente | Puerto del equipo | Dependencia |
| --- | --- | --- |
| MySQL de Usuarios | 3307 | Docker |
| MySQL de SectorAsiento | 3308 | Docker |
| Usuarios | 8004 | Su MySQL |
| SectorAsiento | 8002 | Su MySQL; Eventos para operar reservas |
| API Gateway | 8080 | Los servicios a los que se hagan solicitudes |
| Eventos | 8001 propuesto | Proyecto pendiente de completar |
| Compras | 8003 propuesto | Proyecto pendiente de completar |

8001 y 8003 son destinos provisionales de Feign, no puertos ya configurados en esos proyectos.

Orden recomendado: **Docker -> bases de datos -> Usuarios y SectorAsiento -> Gateway -> Postman**. Cuando Eventos este implementado, iniciarlo antes de probar el flujo de reservas. El Gateway puede arrancar con un proveedor apagado, pero no podra atender solicitudes dirigidas a el.

## 3. Levantar las dos bases

En una terminal ubicada en la raiz TicketYa:

```powershell
docker compose -f ./msv-usuario/docker-compose.yml up -d --wait
docker compose -f ./msv-SectoerAsiento/compose.mysql.yml up -d --wait
```

Revisar que los contenedores esten saludables:

```powershell
docker compose -f ./msv-usuario/docker-compose.yml ps
docker compose -f ./msv-SectoerAsiento/compose.mysql.yml ps
```

Estas configuraciones crean bases distintas:

| Base | Puerto | Usuario de desarrollo | Password de desarrollo |
| --- | --- | --- | --- |
| usuarios | 3307 | usuario_app | usuario_dev |
| sectores_asientos | 3308 | sector_app | sector_dev |

Los valores coinciden con application.properties de cada servicio. Los comandos presuponen que no se han definido DB_URL, DB_USERNAME o DB_PASSWORD con otros valores. Si se personalizan, mantener la misma configuracion entre Compose y Spring Boot. Una variable DB_PASSWORD definida globalmente afecta ambos Compose; es preferible usar terminales separadas si se quieren claves distintas. Cambiar una variable no cambia las credenciales de un volumen MySQL ya inicializado.

## 4. Iniciar Usuarios

En una terminal nueva, ubicada en la raiz TicketYa:

```powershell
$env:JAVA_HOME='C:/Users/franc/.jdks/openjdk-26.0.2'
Set-Location ./msv-usuario
.\mvnw.cmd spring-boot:run
```

Mantener esta terminal abierta. Esperar el mensaje `Started MsvUsuarioApplication`.

Direccion: http://localhost:8004. El perfil local dev crea, si no existe, el administrador de prueba indicado en el README de Usuarios: admin@admin.com / 1234.

## 5. Iniciar SectorAsiento

En otra terminal nueva, ubicada en la raiz TicketYa:

```powershell
$env:JAVA_HOME='C:/Users/franc/.jdks/openjdk-26.0.2'
$env:MSVC_EVENTOS_URL='http://localhost:8001'
$env:MSVC_COMPRAS_URL='http://localhost:8003'
Set-Location ./msv-SectoerAsiento
.\mvnw.cmd spring-boot:run
```

Esperar `Started MsvSectoerAsientoApplication` y mantener la terminal abierta.

Direccion: http://localhost:8002. Puede iniciar sin Eventos ni Compras: Feign no realiza llamadas a esos proveedores durante el arranque. El CRUD de sectores y asientos funciona con la base local. Configurar inventario, reservar, confirmar y devolver si consultan Eventos; por eso esas operaciones requieren su endpoint implementado y disponible.

La documentacion OpenAPI esta en http://localhost:8002/openapi.json.

## 6. Iniciar API Gateway

En otra terminal nueva, ubicada en la raiz TicketYa:

```powershell
$env:JAVA_HOME='C:/Users/franc/.jdks/openjdk-26.0.2'
$env:MSVC_USUARIOS_URL='http://localhost:8004'
$env:MSVC_SECTORES_URL='http://localhost:8002'
Set-Location ./api-gateway
.\mvnw.cmd spring-boot:run
```

Esperar `Started ApiGatewayApplication`. Direccion: http://localhost:8080.

El Gateway enruta usuarios/autenticacion y sectores/asientos/reservas/inventario. El catalogo general de Eventos y los endpoints de Compras todavia no tienen rutas propias configuradas. Las llamadas Feign salen directamente del microservicio al proveedor, usando sus URLs configuradas.

## 7. Comprobar desde PowerShell o Postman

En una terminal libre:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8002/sectores' -Method Get
Invoke-RestMethod -Uri 'http://localhost:8080/sectores' -Method Get
```

Ambas consultas deben responder 200, aunque la lista este vacia. Si PowerShell no muestra contenido porque la lista esta vacia, comprobar el codigo HTTP:

```powershell
(Invoke-WebRequest -Uri 'http://localhost:8080/sectores').StatusCode
```

Ejemplo de creacion a traves del Gateway:

```powershell
$sectorPrueba = @{
    idLugar = 1
    nombre = 'Platea de prueba'
    tipo = 'NUMERADO'
    capacidadTotal = 1
} | ConvertTo-Json

$sectorCreado = Invoke-RestMethod -Uri 'http://localhost:8080/sectores' -Method Post -ContentType 'application/json' -Body $sectorPrueba
$sectorCreado

$asientoPrueba = @{
    idSector = $sectorCreado.id
    fila = 'A'
    columna = '1'
} | ConvertTo-Json

Invoke-RestMethod -Uri 'http://localhost:8080/asientos' -Method Post -ContentType 'application/json' -Body $asientoPrueba
```

Esto crea datos de prueba locales. idLugar se guarda como referencia externa; el CRUD fisico no comprueba su existencia en Eventos. Para configurar inventario mas adelante, el sector debe pertenecer al lugar real del evento.

Login local de Usuarios por Gateway:

```powershell
$loginPrueba = @{ email = 'admin@admin.com'; password = '1234' } | ConvertTo-Json
Invoke-RestMethod -Uri 'http://localhost:8080/auth/login' -Method Post -ContentType 'application/json' -Body $loginPrueba
```

Para los endpoints protegidos de Usuarios, usar el accessToken recibido como Bearer en Postman. El CRUD de SectorAsiento corresponde a la entrega 1 y aun no exige JWT.

Para el flujo de inventario, importar `msv-SectoerAsiento/TicketYa-SectorAsiento.postman_collection.json`; los pasos estan en el README del microservicio. Hace falta Eventos funcionando para completar ese flujo.

## 8. Estado de Eventos y Compras

**Eventos:** la carpeta msv-evento contiene fuentes, pero al revisar esta guia no tenia pom.xml, clase de arranque ni application.properties; su docker-compose.yaml contiene el marcador `$END$`. msv-eventos solo contenia target. Por lo tanto, ninguna de esas carpetas permite seguir actualmente un comando Maven de arranque valido. No utilizar ese Compose incompleto.

Para habilitar Eventos hay que completar el proyecto Spring Boot, su base, configuracion, clase principal y los endpoints. SectorAsiento espera `GET http://localhost:8001/eventos/{id}` con este contrato propuesto:

```json
{
  "id": 1,
  "idLugar": 1,
  "nombre": "Recital",
  "idOrganizador": 1,
  "fechaHora": "2030-12-20T21:00:00",
  "estado": null,
  "estadoPublicacion": "PUBLICADO"
}
```

La fecha debe ser futura y el evento debe estar publicado para vender. Si Eventos se ejecuta en otro puerto, modificar MSVC_EVENTOS_URL en la terminal de SectorAsiento antes de iniciarlo. No se inicio ni completo Eventos como parte de la escritura de esta guia.

**Compras:** msv-compraEntardaSol tiene un pom.xml incompleto para Spring Boot: dependencias sin versiones/BOM y sin clase de arranque encontrada. Sus entidades tambien tienen referencias pendientes. Todavia no tiene un arranque operativo. El destino 8003 es provisional. No hace falta levantarlo para probar inventario: Postman puede simular reservar, confirmar pago y liberar. Eso no prueba cobros, reintegros monetarios ni emision de QR.

## 9. Alternativa con IntelliJ

Levantar primero las bases con los comandos de Docker. Abrir cada proyecto ejecutable desde su pom.xml y dejar que IntelliJ sincronice Maven. Seleccionar un Project SDK Java 21 o superior.

Ejecutar estas clases en configuraciones separadas:

- Usuarios: `com.msvusuario.MsvUsuarioApplication`.
- SectorAsiento: `com.universidad.msvsectoerasiento.MsvSectoerAsientoApplication`.
- Gateway: `com.apigateway.ApiGatewayApplication`.

En cada configuracion, usar la carpeta del microservicio como Working directory. Si se necesitan URLs o credenciales diferentes, definirlas en Environment variables de esa configuracion. No hace falta ejecutar tambien Maven por terminal: usar una sola instancia de cada servicio para evitar puertos ocupados.

## 10. Problemas habituales

| Mensaje o sintoma | Que revisar |
| --- | --- |
| Docker no puede conectarse al motor | Abrir Docker Desktop y esperar que docker version muestre Server. |
| JAVA_HOME no definido o apunta a una ruta invalida | Definir JAVA_HOME en esa misma terminal, apuntando a la carpeta del JDK. |
| Puerto 8002, 8004 u 8080 ocupado | Detener la instancia anterior en IntelliJ o con Ctrl+C en su terminal. |
| MySQL: Connection refused / Communications link failure | Revisar compose ps y esperar el healthcheck. |
| MySQL: Access denied | Comparar credenciales y variables; un volumen existente conserva las claves con las que fue creado. |
| GET /sectores directo funciona pero por Gateway falla | Revisar que Gateway este iniciado y MSVC_SECTORES_URL apunte a 8002. |
| Configurar inventario o reservar devuelve 503 | Eventos esta apagado, su URL es incorrecta o no responde a tiempo. |
| Eventos devuelve 404 | Falta GET /eventos/{id} o el evento solicitado no existe. |
| Reserva devuelve 409 | Revisar publicacion, fecha, cupos, asientos, limite por cliente y clave de idempotencia. |

Logs de las bases, desde TicketYa:

```powershell
docker compose -f ./msv-usuario/docker-compose.yml logs --tail 50 mysql-usuario
docker compose -f ./msv-SectoerAsiento/compose.mysql.yml logs --tail 50 mysql-sector
```

## 11. Detener y ejecutar pruebas

Detener cada aplicacion Java con Ctrl+C en su terminal, o con Stop en IntelliJ. Desde una terminal ubicada en TicketYa:

```powershell
docker compose -f ./msv-usuario/docker-compose.yml stop
docker compose -f ./msv-SectoerAsiento/compose.mysql.yml stop
```

Los datos quedan en sus volumenes. Estos comandos no eliminan las bases.

Las pruebas automaticas de SectorAsiento no requieren Docker, Eventos ni Compras, porque utilizan H2 y un proveedor HTTP de prueba:

```powershell
$env:JAVA_HOME='C:/Users/franc/.jdks/openjdk-26.0.2'
Set-Location ./msv-SectoerAsiento
.\mvnw.cmd test
```

La prueba con MySQL tiene su base descartable y sus instrucciones especificas en el README de SectorAsiento. No usar una base con datos a conservar para esa suite.

Esta guia se preparo leyendo la configuracion existente; no inicia procesos ni cambia servicios, variables permanentes o bases de datos.
