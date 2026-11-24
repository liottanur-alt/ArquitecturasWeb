# TicketYa · Documentación del sistema

TicketYa gestiona eventos, inventario de entradas, fila virtual, compras, acceso por QR, devoluciones, notificaciones y recomendaciones. Los valores de ejemplo son ilustrativos. Usá los IDs devueltos por tu instancia.

## 1. Arranque local

1. Abrí Docker Desktop y una terminal PowerShell en la raíz del proyecto.
2. Si falta `.env`, copialo desde `.env.example`. Definí `JWT_SECRET` con al menos 32 bytes UTF-8 y `ADMIN_SEED_PASSWORD` con una contraseña de desarrollo. Mantené el secreto JWT estable y guardá ambas credenciales fuera de Git.
3. Validá y levantá los servicios.

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# Completá .env antes de continuar.
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 600
docker compose ps -a
Invoke-RestMethod http://localhost:8080/actuator/health
```

Esperá `status: UP`. `mysql-esquemas` inicializa las bases y debe terminar con código 0. Para detener el entorno y conservar los datos usá `docker compose stop`.

## 2. Arquitectura y datos

| Servicio | Dirección | Datos y función |
| --- | --- | --- |
| Gateway | localhost:8080 | Valida JWT y enruta a Usuarios, Eventos, Sectores y Compras. |
| Usuarios | localhost:8004 | MongoDB `usuarios`. Colecciones `usuarios`, `roles` y `secuencias`. Autenticación, preferencias, favoritos y recomendaciones. |
| Eventos | localhost:8005 | MySQL `eventos`. Tablas `eventos`, `lugares` y `artistas`. Redis mantiene la fila virtual. |
| Sectores | localhost:8002 | MySQL `sectores_asientos`. Tablas principales `sectores`, `asientos`, `inventarios_evento`, `sectores_evento`, `asientos_evento`, `reservas` y `devoluciones_inventario`. |
| Compras | localhost:8003 | MySQL `compra_entrada_sol`. Tablas principales `compra`, `compra_asientos`, `entrada` y `solicitud`. Pagos y reintegros simulados. |
| Notificaciones | notificaciones:8006 en Docker | MySQL `notificaciones`. Tablas `sucesos_negocio`, `notificaciones` y `logs_aplicacion`. Bitácora, reportes y correo. |

MongoDB 8 publica `127.0.0.1:27017`. MySQL 8.4 publica `127.0.0.1:3306` por defecto. Redis 7 escucha en `redis:6379` dentro de Docker y usa persistencia AOF. Los volúmenes son `ticketya-usuarios-local_usuarios_mongo`, `ticketya_mysql_data` y `ticketya_redis_data`. El inicializador MySQL también aprovisiona un esquema `usuarios`; el microservicio de Usuarios usa MongoDB. Los servicios relacionales usan Hibernate con `DDL_AUTO=update` por defecto.

| Variables | Configuración |
| --- | --- |
| `JWT_SECRET`, `ADMIN_SEED_PASSWORD` | Obligatorias para Compose. |
| `MYSQL_PORT`, `MYSQL_ROOT_PASSWORD` | Puerto local y clave de administración MySQL. |
| `USUARIO_DB_PASSWORD`, `EVENTO_DB_PASSWORD`, `SECTOR_DB_PASSWORD`, `COMPRA_DB_PASSWORD`, `NOTIFICACIONES_DB_PASSWORD` | Claves por esquema. `DB_PASSWORD` es el respaldo común. Con volúmenes existentes, coordiná cambios con las credenciales persistidas. |
| `FILA_MAX_COMPRADORES_SIMULTANEOS` | 10 turnos activos por defecto. Compose fija la duración del turno en 900 segundos. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`, `SMTP_FROM` | Correo de Notificaciones. El servidor SMTP debe ser accesible desde el contenedor. |

## 3. Autenticación, roles y titularidad

Usuarios emite JWT HS256 con issuer `msv-usuario`, duración de 1800 segundos y claims `sub` (ID del usuario) y `roles`. Usá `Authorization: Bearer <accessToken>`. Son públicos el registro, el login y la salud del Gateway. Spring permite `/error` para su manejo de errores.

- `ADMINISTRADOR` gestiona roles, lista y crea fichas de usuarios, resuelve solicitudes y cancela eventos. `POST /usuarios` crea una ficha sin contraseña. Para una cuenta que pueda iniciar sesión, usá el registro público y asigná el rol después.
- `/usuarios/{id}` y subrutas permiten al titular o administrador. `#id.toString() == authentication.name` compara el ID de la URL con el claim `sub`. Si el titular actualiza su perfil, debe enviar su `idRol` actual.
- Crear eventos y listar los propios requiere `ORGANIZADOR` o `ADMINISTRADOR`. Publicar y reprogramar comprueban también titularidad. Editar y finalizar comprueban titularidad en el servicio. El filtro deriva `X-Organizador-Id` del JWT.
- `GET /entradas/qr/{qr}` requiere `PERSONAL_ACCESO` o `ADMINISTRADOR`. Las rutas `PATCH` de uso requieren JWT válido según el código actual.
- Crear compras usa el ID del JWT. Crear una solicitud también verifica que la entrada pertenezca al titular. Las consultas de Compras, Entradas y Solicitudes por `idCliente` carecen del control de titularidad de Usuarios.
- El Gateway bloquea rutas con segmento `interno`. Los servicios autorizan esas rutas directamente con `SERVICIO_INTERNO`. Sectores exige ese rol para reservas. Crear reservas y validar o completar turnos también exige el claim `usuarioId`.
- Notificaciones está en la red Docker y carece de autenticación HTTP propia.

### Crear cuentas y probar roles en Postman

1. Importá [la colección Postman](TicketYa-roles.postman_collection.json). Usá `baseUrl = http://localhost:8080`.
2. En **Inicio - Autenticación**, ejecutá **Registrar**, **Login** y **Mi usuario autenticado**. El registro genera correo y DNI de prueba y crea un `CLIENTE`. El login guarda el token en `accessToken`.
3. Para probar otro rol, elegí `rolObjetivo` en **Variables**. Opciones: `CLIENTE`, `ORGANIZADOR`, `PERSONAL_ACCESO` y `ADMINISTRADOR`.
4. Cargá `adminPassword` como valor local. Usá la contraseña con la que se creó `admin@admin.com`. Cambiar `ADMIN_SEED_PASSWORD` en `.env` después de crear la cuenta no actualiza el hash de MongoDB.
5. Ejecutá **Cuentas y pruebas por rol** en orden. El flujo registra un cliente, autentica al administrador, busca el ID con `GET /roles`, asigna el rol con `PUT /usuarios/{id}` y genera un JWT nuevo. El token anterior conserva su rol hasta que vuelvas a iniciar sesión.

| Rol | GET /roles | GET /api/eventos/mis-eventos | GET /entradas/qr/{qr} con QR existente |
| --- | --- | --- | --- |
| CLIENTE | 403 | 403 | 403 |
| ORGANIZADOR | 403 | 200 | 403 |
| PERSONAL_ACCESO | 403 | 403 | 200 |
| ADMINISTRADOR | 200 | 200 | 200 |

## 4. Flujos paso a paso

### Publicar un evento

1. Iniciá sesión como organizador. Creá un lugar con `POST /api/lugares` y un artista con `POST /api/artistas`.
2. Creá un borrador con `POST /api/eventos`. Usá los IDs en `idLugar` y `participantesIds`, y una fecha futura en formato `YYYY-MM-DDTHH:mm:ss`. Para probar devolución, elegí una fecha a más de 72 horas.
3. Creá el sector en ese lugar con `POST /sectores`. Para `NUMERADO`, cargá los asientos físicos con `POST /asientos`.
4. Ejecutá `PUT /eventos/{idEvento}/inventario` con `limitePorCliente`. Ejecutá `PUT /eventos/{idEvento}/sectores` con `idSector`, `precio` y `capacidadHabilitada`. En un sector numerado, la capacidad habilitada debe coincidir con los asientos creados.
5. Como titular, ejecutá `PATCH /api/eventos/{id}/publicar` sin body. Verificá catálogo y disponibilidad. La edición general aplica a borradores. Para reprogramar un publicado activo usá `PATCH /api/eventos/{id}/programacion`.

### Comprar y usar el QR

1. Iniciá sesión como cliente. Consultá el inventario con `GET /eventos/{idEvento}/sectores/{idSector}/disponibilidad`.
2. Si el evento tiene `demanda: true`, entrá con `POST /api/eventos/{eventoId}/fila` y consultá `GET /api/eventos/{eventoId}/fila/mi-turno`. Agregá `autorizacionCompra` al pedido cuando te habilite.
3. Enviá `POST /compras`. En `CAMPO` usá `cantidadEntradas`. En `NUMERADO` usá `idsAsientos`. Conservá el mismo pedido y la misma `claveIdempotencia` si reintentás.
4. Guardá el ID de compra y simulá el pago con `PUT /compras/{id}/pago?aprobado=true`. Para simular rechazo, usá `aprobado=false`. Va sin body.
5. Consultá `GET /entradas/compra/{idCompra}` y copiá el `qr`. Cambiá a un token de personal de acceso o administrador y llamá a `GET /entradas/qr/{qr}`.
6. Para marcar el ingreso usá `PATCH /entradas/qr/{qr}/uso?idEvento={idEvento}`. Usá otra entrada para probar devolución.

### Tramitar una devolución

1. Como titular, enviá `POST /solicitudes` con `idEntrada`, `idCliente` y `descripcion`. El evento debe admitir devoluciones y faltar como mínimo 72 horas.
2. Como administrador, consultá pendientes y ejecutá `PUT /solicitudes/{id}/resolver` con `idAdministrador` y `estadoSolicitud` igual a `ACEPTADA` o `RECHAZADA`.
3. Verificá solicitud, entrada e inventario. Al aceptar, Compras coordina el reintegro simulado y la devolución de inventario.
4. Consultá la bitácora o el reporte de Notificaciones dentro de Docker. Los sucesos, como compra confirmada, devolución aprobada y cambios de evento, usan claves idempotentes. El correo saliente depende de SMTP.

### Probar recomendaciones

1. Como titular, ejecutá `PUT /usuarios/{id}/preferencias` para reemplazar tipos preferidos y ciudad.
2. Agregá un artista con `PUT /usuarios/{id}/artistas-favoritos/{idArtista}` sin body.
3. Consultá `GET /usuarios/{id}/recomendaciones?limite=10`. Las variantes `/por-preferencias` y `/por-compras` muestran sus fuentes por separado.
4. La recomendación unificada combina preferencias y compras en común. Filtra eventos ya comprados y verifica disponibilidad si las dependencias responden. Ante respuestas parciales, revisá los indicadores de historial e inventario.

## 5. MongoDB, diagnóstico y pruebas

En DataGrip, conectate a `mongodb://localhost:27017/usuarios`. Abrí la consola MongoDB y ejecutá:

```javascript
db.getSiblingDB("usuarios").getCollection("usuarios").find(
  { email: "admin@admin.com" },
  { _id: 1, email: 1, rol: 1 }
)
db.getSiblingDB("usuarios").getCollection("roles").find({}, { _id: 1, name: 1 })
```

El campo `rol` es un DBRef al `_id` de la colección `roles`. Por terminal podés entrar con `docker compose exec mongodb mongosh usuarios`.

| Error | Qué revisar |
| --- | --- |
| Variables obligatorias de Compose | Completá `.env` y ejecutá `docker compose config --quiet`. |
| 404 en login | Verificá método y URL. Compará Gateway 8080 y Usuarios 8004. Si hay una imagen antigua, reconstruí con `docker compose up --build -d --wait`. |
| 401 en login | Revisá email, clave y valor efectivo de `adminPassword` en Postman. |
| 401 en una ruta protegida | Volvé a iniciar sesión y comprobá que la solicitud herede el Bearer. |
| 403 | Revisá rol, sesión activa e ID del titular. El path de Eventos es `/api/eventos`, en plural. |
| 409 | Revisá duplicados, estado, cupo o reutilización de la clave de idempotencia. |
| Problemas de salud | Corré `docker compose ps -a` y `docker compose logs --tail 100 gateway usuarios mysql-esquemas`. |

Con PowerShell 7 y el stack saludable, ejecutá desde la raíz:

```powershell
pwsh -NoProfile -File ./tests/system/Test-System.ps1
pwsh -NoProfile -File ./tests/system/Test-System.ps1 -AuditSecurity
pwsh -NoProfile -File ./tests/system/Test-Load.ps1 -Concurrency 10 -Requests 500
pwsh -NoProfile -File ./tests/system/Test-Concurrencia-MultiInstancia.ps1
```

Integración crea fixtures. Corré carga después de integración. Multi-instancia usa contenedores aislados. Las pruebas Java se ejecutan con Java 21 y `./mvnw.cmd test` dentro de cada módulo. Consultá [pruebas de sistema](tests/system/README.md) y [pruebas de recomendaciones](tests/recomendaciones/README.md).

## 6. Catálogo HTTP

La URL base es `http://localhost:8080` salvo las rutas directas. Usá **Body → raw → JSON** para los ejemplos, **Params** para query y **Body → none** para operaciones sin body.


### Usuarios

| Método | Ruta | Acceso | Entrada |
| --- | --- | --- | --- |
| `POST` | `/auth/login` | Pública | [JSON](#body-1) |
| `POST` | `/auth/registro` | Pública | [JSON](#body-2) |
| `GET` | `/interno/notificaciones/destinatarios/{id}` | Directa, SERVICIO_INTERNO | Sin body |
| `POST` | `/interno/notificaciones/destinatarios/favoritos` | Directa, SERVICIO_INTERNO | [JSON](#body-3) |
| `GET` | `/roles` | ADMINISTRADOR | Sin body |
| `POST` | `/roles` | ADMINISTRADOR | [JSON](#body-4) |
| `DELETE` | `/roles/{id}` | ADMINISTRADOR | Sin body |
| `GET` | `/roles/{id}` | ADMINISTRADOR | Sin body |
| `PUT` | `/roles/{id}` | ADMINISTRADOR | [JSON](#body-5) |
| `GET` | `/usuarios` | ADMINISTRADOR | Sin body |
| `POST` | `/usuarios` | ADMINISTRADOR | [JSON](#body-6) |
| `DELETE` | `/usuarios/{id}` | Titular o ADMINISTRADOR | Sin body |
| `GET` | `/usuarios/{id}` | Titular o ADMINISTRADOR | Sin body |
| `PUT` | `/usuarios/{id}` | Titular o ADMINISTRADOR | [JSON](#body-7) |
| `GET` | `/usuarios/{id}/artistas-favoritos` | Titular o ADMINISTRADOR | Sin body |
| `DELETE` | `/usuarios/{id}/artistas-favoritos/{idArtista}` | Titular o ADMINISTRADOR | Sin body |
| `PUT` | `/usuarios/{id}/artistas-favoritos/{idArtista}` | Titular o ADMINISTRADOR | Sin body |
| `GET` | `/usuarios/{id}/preferencias` | Titular o ADMINISTRADOR | Sin body |
| `PUT` | `/usuarios/{id}/preferencias` | Titular o ADMINISTRADOR | [JSON](#body-8) |
| `GET` | `/usuarios/{id}/recomendaciones` | Titular o ADMINISTRADOR | Sin body. Query `limite` (opcional) |
| `GET` | `/usuarios/{id}/recomendaciones/por-compras` | Titular o ADMINISTRADOR | Sin body |
| `GET` | `/usuarios/{id}/recomendaciones/por-preferencias` | Titular o ADMINISTRADOR | Sin body |

### Eventos

| Método | Ruta | Acceso | Entrada |
| --- | --- | --- | --- |
| `GET` | `/api/artistas` | JWT | Sin body |
| `POST` | `/api/artistas` | JWT | [JSON](#body-9) |
| `DELETE` | `/api/artistas/{id}` | JWT | Sin body |
| `GET` | `/api/artistas/{id}` | JWT | Sin body |
| `PUT` | `/api/artistas/{id}` | JWT | [JSON](#body-10) |
| `GET` | `/api/eventos` | JWT | Sin body |
| `POST` | `/api/eventos` | ORGANIZADOR o ADMINISTRADOR | [JSON](#body-11) |
| `POST` | `/api/eventos/{eventoId}/fila` | JWT | Sin body |
| `GET` | `/api/eventos/{eventoId}/fila/mi-turno` | JWT | Sin body |
| `DELETE` | `/api/eventos/{id}` | JWT | Sin body |
| `GET` | `/api/eventos/{id}` | JWT | Sin body |
| `PUT` | `/api/eventos/{id}` | JWT y titular del evento | [JSON](#body-12) |
| `PATCH` | `/api/eventos/{id}/cancelar` | ADMINISTRADOR o JWT de servicio | Sin body |
| `PATCH` | `/api/eventos/{id}/finalizar` | JWT y titular del evento | Sin body |
| `PATCH` | `/api/eventos/{id}/programacion` | ORGANIZADOR o ADMINISTRADOR, y titular | [JSON](#body-13) |
| `PATCH` | `/api/eventos/{id}/publicar` | ORGANIZADOR o ADMINISTRADOR, y titular | Sin body |
| `PATCH` | `/api/eventos/{id}/suspender` | JWT | Sin body |
| `GET` | `/api/eventos/buscar` | JWT | Sin body. Query `tipo` (opcional), `idOrganizador` (opcional), `idParticipante` (opcional), `ciudad` (opcional), `fechaDesde` (opcional), `fechaHasta` (opcional), `pagina` (opcional), `tamanio` (opcional), `ordenar` (opcional) |
| `GET` | `/api/eventos/interno/{id}` | Directa, SERVICIO_INTERNO | Sin body |
| `PATCH` | `/api/eventos/interno/{id}/disponibilidad` | Directa, SERVICIO_INTERNO | Sin body. Query `agotado` |
| `GET` | `/api/eventos/mis-eventos` | ORGANIZADOR o ADMINISTRADOR | Sin body |
| `GET` | `/api/lugares` | JWT | Sin body |
| `POST` | `/api/lugares` | JWT | [JSON](#body-14) |
| `DELETE` | `/api/lugares/{id}` | JWT | Sin body |
| `GET` | `/api/lugares/{id}` | JWT | Sin body |
| `PUT` | `/api/lugares/{id}` | JWT | [JSON](#body-15) |
| `POST` | `/interno/eventos/recomendaciones/candidatos` | Directa, SERVICIO_INTERNO | [JSON](#body-16) |
| `POST` | `/interno/eventos/recomendaciones/por-ids` | Directa, SERVICIO_INTERNO | [JSON](#body-17) |
| `POST` | `/interno/fila/autorizaciones/completar` | Directa, SERVICIO_INTERNO | [JSON](#body-18) |
| `POST` | `/interno/fila/autorizaciones/validar` | Directa, SERVICIO_INTERNO | [JSON](#body-19) |

### Sectores

| Método | Ruta | Acceso | Entrada |
| --- | --- | --- | --- |
| `GET` | `/asientos` | JWT | Sin body. Query `idSector` (opcional) |
| `POST` | `/asientos` | JWT | [JSON](#body-20) |
| `DELETE` | `/asientos/{id}` | JWT | Sin body |
| `GET` | `/asientos/{id}` | JWT | Sin body |
| `PUT` | `/asientos/{id}` | JWT | [JSON](#body-21) |
| `PUT` | `/eventos/{idEvento}/inventario` | JWT | [JSON](#body-22) |
| `POST` | `/eventos/{idEvento}/inventario/cancelacion` | JWT | Sin body |
| `GET` | `/eventos/{idEvento}/sectores` | JWT | Sin body |
| `PUT` | `/eventos/{idEvento}/sectores` | JWT | [JSON](#body-23) |
| `GET` | `/eventos/{idEvento}/sectores/{idSector}/disponibilidad` | JWT | Sin body |
| `POST` | `/interno/inventario/disponibilidad` | Directa, SERVICIO_INTERNO | [JSON](#body-24) |
| `POST` | `/interno/inventario/precios-minimos` | Directa, SERVICIO_INTERNO | [JSON](#body-25) |
| `POST` | `/reservas` | Directa, SERVICIO_INTERNO | [JSON](#body-26) |
| `GET` | `/reservas/{id}` | Directa, SERVICIO_INTERNO | Sin body |
| `POST` | `/reservas/{id}/confirmacion` | Directa, SERVICIO_INTERNO | Sin body |
| `POST` | `/reservas/{id}/devoluciones` | Directa, SERVICIO_INTERNO | [JSON](#body-27) |
| `POST` | `/reservas/{id}/liberacion` | Directa, SERVICIO_INTERNO | Sin body |
| `GET` | `/sectores` | JWT | Sin body. Query `idLugar` (opcional) |
| `POST` | `/sectores` | JWT | [JSON](#body-28) |
| `DELETE` | `/sectores/{id}` | JWT | Sin body |
| `GET` | `/sectores/{id}` | JWT | Sin body |
| `PUT` | `/sectores/{id}` | JWT | [JSON](#body-29) |

### Compras

| Método | Ruta | Acceso | Entrada |
| --- | --- | --- | --- |
| `POST` | `/compras` · CAMPO | JWT | [JSON](#body-30) |
| `POST` | `/compras` · NUMERADO | JWT | [JSON](#body-31) |
| `GET` | `/compras/{id}` | JWT | Sin body |
| `PUT` | `/compras/{id}/pago` | JWT | Sin body. Query `aprobado` |
| `GET` | `/compras/cliente/{idCliente}` | JWT | Sin body |
| `POST` | `/compras/eventos/{idEvento}/cancelacion` | JWT | Sin body |
| `GET` | `/entradas/{id}` | JWT | Sin body |
| `PATCH` | `/entradas/{id}/uso` | JWT | Sin body. Query `idEvento` |
| `GET` | `/entradas/cliente/{idCliente}` | JWT | Sin body |
| `GET` | `/entradas/compra/{idCompra}` | JWT | Sin body |
| `GET` | `/entradas/qr/{qr}` | PERSONAL_ACCESO o ADMINISTRADOR | Sin body |
| `PATCH` | `/entradas/qr/{qr}/uso` | JWT | Sin body. Query `idEvento` |
| `GET` | `/interno/notificaciones/eventos/{id}/compradores` | Directa, SERVICIO_INTERNO | Sin body. Query `cancelacion` |
| `GET` | `/interno/recomendaciones/clientes/{idCliente}` | Directa, SERVICIO_INTERNO | Sin body |
| `GET` | `/interno/recomendaciones/clientes/{idCliente}/historial-valido` | Directa, SERVICIO_INTERNO | Sin body |
| `POST` | `/solicitudes` | JWT y titular de la entrada | [JSON](#body-32) |
| `GET` | `/solicitudes/{id}` | JWT | Sin body |
| `PUT` | `/solicitudes/{id}/resolver` | ADMINISTRADOR | [JSON](#body-33) |
| `GET` | `/solicitudes/cliente/{idCliente}` | JWT | Sin body |
| `GET` | `/solicitudes/entrada/{idEntrada}` | JWT | Sin body |
| `GET` | `/solicitudes/pendientes` | JWT | Sin body |

### Notificaciones

| Método | Ruta | Acceso | Entrada |
| --- | --- | --- | --- |
| `GET` | `/api/bitacora` | Directa en Docker, sin JWT | Sin body. Query `idEvento` |
| `GET` | `/api/logs` | Directa en Docker, sin JWT | Sin body. Query `servicio` (opcional), `desde`, `hasta` |
| `POST` | `/api/logs` | Directa en Docker, sin JWT | [JSON](#body-34) |
| `GET` | `/api/notificaciones/clientes/{idCliente}` | Directa en Docker, sin JWT | Sin body |
| `GET` | `/api/reportes/eventos/{idEvento}` | Directa en Docker, sin JWT | Sin body |
| `POST` | `/api/sucesos` | Directa en Docker, sin JWT | [JSON](#body-35) |

### Salud y contratos OpenAPI

- `GET /actuator/health` es público en el Gateway y en los puertos directos de Usuarios, Eventos, Compras y Sectores.
- `GET /openapi.json` se llama directo a esos cuatro microservicios. Usuarios permite acceso público. Los contratos de Eventos, Compras y Sectores requieren JWT.
- `POST /interno/inventario/precios-minimos` consulta el precio mínimo por evento en Sectores. Figura en el código actual y se puede probar directamente con un JWT de servicio y el body del catálogo.

## 7. Ejemplos de body

<a id="body-1"></a>

### POST /auth/login

```json
{
  "email": "cliente@example.com",
  "password": "EjemploLocal2027!"
}
```

<a id="body-2"></a>

### POST /auth/registro

```json
{
  "nombre": "Cliente de prueba",
  "email": "cliente@example.com",
  "dni": "35123456",
  "ciudad": "Tandil",
  "password": "EjemploLocal2027!"
}
```

<a id="body-3"></a>

### POST /interno/notificaciones/destinatarios/favoritos

```json
{
  "ciudad": "Tandil",
  "artistas": [
    1,
    2
  ]
}
```

<a id="body-4"></a>

### POST /roles

```json
{
  "name": "ORGANIZADOR"
}
```

<a id="body-5"></a>

### PUT /roles/{id}

```json
{
  "name": "ORGANIZADOR"
}
```

<a id="body-6"></a>

### POST /usuarios

```json
{
  "nombre": "Ana Pérez",
  "email": "ana@example.com",
  "dni": "35123456",
  "ciudad": "Tandil",
  "idRol": 1
}
```

<a id="body-7"></a>

### PUT /usuarios/{id}

```json
{
  "nombre": "Ana Pérez",
  "email": "ana@example.com",
  "dni": "35123456",
  "ciudad": "Tandil",
  "idRol": 1
}
```

<a id="body-8"></a>

### PUT /usuarios/{id}/preferencias

```json
{
  "tiposEventoPreferidos": [
    "RECITAL",
    "TEATRO"
  ],
  "ciudadPreferida": "Tandil"
}
```

<a id="body-9"></a>

### POST /api/artistas

```json
{
  "nombre": "Banda del Centro",
  "genero": "Rock"
}
```

<a id="body-10"></a>

### PUT /api/artistas/{id}

```json
{
  "nombre": "Banda del Centro",
  "genero": "Rock"
}
```

<a id="body-11"></a>

### POST /api/eventos

```json
{
  "nombre": "Festival del Centro",
  "descripcion": "Música en vivo",
  "tipo": "RECITAL",
  "participantesIds": [
    1
  ],
  "fechaHora": "2027-06-15T20:30:00",
  "idLugar": 1,
  "demanda": false
}
```

<a id="body-12"></a>

### PUT /api/eventos/{id}

```json
{
  "nombre": "Festival del Centro",
  "descripcion": "Música en vivo",
  "tipo": "RECITAL",
  "participantesIds": [
    1
  ],
  "fechaHora": "2027-06-15T20:30:00",
  "idLugar": 1,
  "demanda": false
}
```

<a id="body-13"></a>

### PATCH /api/eventos/{id}/programacion

```json
{
  "fechaHora": "2027-06-16T20:30:00",
  "idLugar": 1
}
```

<a id="body-14"></a>

### POST /api/lugares

```json
{
  "nombre": "Teatro del Centro",
  "direccion": "San Martín 123",
  "latitud": -37.3288,
  "longitud": -59.1367,
  "ciudad": "Tandil",
  "tipo": "TEATRO"
}
```

<a id="body-15"></a>

### PUT /api/lugares/{id}

```json
{
  "nombre": "Teatro del Centro",
  "direccion": "San Martín 123",
  "latitud": -37.3288,
  "longitud": -59.1367,
  "ciudad": "Tandil",
  "tipo": "TEATRO"
}
```

<a id="body-16"></a>

### POST /interno/eventos/recomendaciones/candidatos

```json
{
  "tiposEventoPreferidos": [
    "RECITAL"
  ],
  "artistasFavoritos": [
    1
  ],
  "ciudadPreferida": "Tandil",
  "fechaDesde": "2027-01-01T00:00:00"
}
```

<a id="body-17"></a>

### POST /interno/eventos/recomendaciones/por-ids

```json
{
  "idsEventos": [
    1,
    2
  ]
}
```

<a id="body-18"></a>

### POST /interno/fila/autorizaciones/completar

```json
{
  "eventoId": 1,
  "clienteId": 5,
  "autorizacionCompra": "REEMPLAZAR_CON_EL_VALOR_DE_LA_FILA",
  "idOperacion": "compra-002"
}
```

<a id="body-19"></a>

### POST /interno/fila/autorizaciones/validar

```json
{
  "eventoId": 1,
  "clienteId": 5,
  "autorizacionCompra": "REEMPLAZAR_CON_EL_VALOR_DE_LA_FILA",
  "idOperacion": "compra-002"
}
```

<a id="body-20"></a>

### POST /asientos

```json
{
  "idSector": 2,
  "fila": "A",
  "columna": "1"
}
```

<a id="body-21"></a>

### PUT /asientos/{id}

```json
{
  "idSector": 2,
  "fila": "A",
  "columna": "1"
}
```

<a id="body-22"></a>

### PUT /eventos/{idEvento}/inventario

```json
{
  "limitePorCliente": 4
}
```

<a id="body-23"></a>

### PUT /eventos/{idEvento}/sectores

```json
{
  "idSector": 1,
  "precio": 1500,
  "capacidadHabilitada": 100
}
```

<a id="body-24"></a>

### POST /interno/inventario/disponibilidad

```json
{
  "idsEventos": [
    1,
    2
  ]
}
```

<a id="body-25"></a>

### POST /interno/inventario/precios-minimos

```json
{
  "idsEventos": [
    1,
    2
  ]
}
```

<a id="body-26"></a>

### POST /reservas

```json
{
  "claveIdempotencia": "reserva-001",
  "idCliente": 5,
  "idEvento": 1,
  "idSector": 1,
  "cantidad": 2,
  "idAsientos": []
}
```

<a id="body-27"></a>

### POST /reservas/{id}/devoluciones

```json
{
  "claveIdempotencia": "devolucion-001",
  "cantidad": 1,
  "solicitadaEn": "2027-06-14T15:30:00Z",
  "idAsientos": []
}
```

<a id="body-28"></a>

### POST /sectores

```json
{
  "idLugar": 1,
  "nombre": "Campo",
  "tipo": "CAMPO",
  "capacidadTotal": 100
}
```

<a id="body-29"></a>

### PUT /sectores/{id}

```json
{
  "idLugar": 1,
  "nombre": "Campo",
  "tipo": "CAMPO",
  "capacidadTotal": 100
}
```

<a id="body-30"></a>

### POST /compras · CAMPO

```json
{
  "claveIdempotencia": "compra-campo-001",
  "idCliente": 5,
  "idEvento": 1,
  "idSector": 1,
  "cantidadEntradas": 2,
  "medioDePago": "TARJETA"
}
```

<a id="body-31"></a>

### POST /compras · NUMERADO

```json
{
  "claveIdempotencia": "compra-numerada-001",
  "idCliente": 5,
  "idEvento": 1,
  "idSector": 2,
  "idsAsientos": [
    11,
    12
  ],
  "medioDePago": "TARJETA",
  "autorizacionCompra": "VALOR_DE_LA_FILA"
}
```

<a id="body-32"></a>

### POST /solicitudes

```json
{
  "idEntrada": 11,
  "idCliente": 5,
  "descripcion": "Quiero solicitar la devolución de la entrada."
}
```

<a id="body-33"></a>

### PUT /solicitudes/{id}/resolver

```json
{
  "idAdministrador": 1,
  "estadoSolicitud": "ACEPTADA"
}
```

<a id="body-34"></a>

### POST /api/logs

```json
{
  "servicio": "compras",
  "nivel": "INFO",
  "mensaje": "Compra confirmada",
  "idCorrelacion": "compra-11",
  "fecha": "2027-06-14T15:30:00Z"
}
```

<a id="body-35"></a>

### POST /api/sucesos

```json
{
  "claveIdempotencia": "compra-11-confirmada",
  "tipo": "COMPRA_CONFIRMADA",
  "idEvento": 1,
  "idCompra": 11,
  "idCliente": 5,
  "destinatario": "ana@example.com",
  "cantidadEntradas": 2,
  "importe": 3000,
  "capacidadTotal": 100,
  "fechaSuceso": "2027-06-14T15:30:00Z",
  "detalle": "Compra confirmada"
}
```
