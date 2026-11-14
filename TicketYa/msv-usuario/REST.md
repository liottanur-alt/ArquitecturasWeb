# API de usuarios

Las rutas son relativas a la URL del microservicio. Para levantar MySQL y ejecutar
la aplicacion, seguir las instrucciones de [README.md](README.md).

## Usuarios

| Metodo | Ruta | Resultado |
| --- | --- | --- |
| GET | /usuarios | Lista de usuarios |
| GET | /usuarios/{id} | Usuario por ID |
| POST | /usuarios | Crea un usuario (201 y encabezado Location) |
| PUT | /usuarios/{id} | Actualiza todos los datos del usuario |
| DELETE | /usuarios/{id} | Elimina usuario y sus favoritos (204) |

Body para POST y PUT (el rol debe existir):

```json
{
  "nombre": "Ana Perez",
  "email": "ana@example.com",
  "dni": "12345678",
  "ciudad": "Tandil",
  "idRol": 1
}
```

La respuesta incluye esos campos y el `id` del usuario. Email y DNI son unicos.

## Roles

| Metodo | Ruta | Resultado |
| --- | --- | --- |
| GET | /roles | Lista de roles |
| GET | /roles/{id} | Rol por ID |
| POST | /roles | Crea un rol (201 y encabezado Location) |
| PUT | /roles/{id} | Actualiza el nombre del rol |
| DELETE | /roles/{id} | Elimina un rol sin usuarios asociados (204) |

Body para POST y PUT:

```json
{"name": "CLIENTE"}
```

`name` es un enum y solo admite `CLIENTE`, `ORGANIZADOR`, `ADMINISTRADOR`
y `PERSONAL_ACCESO`, en mayusculas. Otros valores o un tipo ausente devuelven 400.
En la base se guarda el nombre del enum, no su posicion numerica.

## Artistas favoritos

| Metodo | Ruta | Resultado |
| --- | --- | --- |
| GET | /usuarios/{id}/artistas-favoritos | Lista de artistas con ID, nombre y genero |
| PUT | /usuarios/{id}/artistas-favoritos/{idArtista} | Agrega un favorito (204, sin body) |
| DELETE | /usuarios/{id}/artistas-favoritos/{idArtista} | Quita un favorito (204) |

El ID del artista debe ser positivo. Al guardar, este servicio persiste solo la
referencia externa. Al listar consulta `GET /api/artistas/{id}` en el servicio
de eventos, configurado mediante `MSV_EVENTO_URL` (por defecto localhost:8005).

Ejemplo de respuesta del listado:

```json
[{"idArtista":123,"nombre":"Soda Stereo","genero":"Rock"}]
```

Sin favoritos devuelve `[]` y no consulta Eventos. Si Eventos no responde devuelve
503; si un artista ya no existe o devuelve datos incompletos devuelve 502.
Los favoritos guardados no se eliminan ante fallos del servicio externo.

## Errores

- 400: campos obligatorios ausentes o invalidos, o JSON incorrecto.
- 404: usuario o rol inexistente.
- 409: email, DNI o nombre de rol duplicado; rol en uso; otra restriccion de integridad.

## Autenticacion diferida

La version activa no requiere token ni contrasena. Crear cuentas con POST /usuarios e idRol obtenido de GET /roles. Las rutas /auth/login y /auth/registro estan retiradas y devuelven 404.

Los roles siguen siendo datos del dominio; en esta etapa no restringen acceso a los endpoints. La autenticacion y autorizacion se incorporaran cuando se restaure y adapte el modulo JWT.

## Verificacion

`mvnw.cmd test` ejecuta pruebas unitarias, persistencia H2 y CRUD HTTP sin token. No requiere MySQL ni Docker.

## Contrato OpenAPI y colección de pruebas

El contrato OpenAPI 3.0.3 está en [src/main/resources/static/openapi.json](src/main/resources/static/openapi.json) y se sirve directamente en http://localhost:8004/openapi.json cuando Usuarios está iniciado.

Importar [TicketYa-Usuarios.postman_collection.json](TicketYa-Usuarios.postman_collection.json) en Postman. Incluye 32 solicitudes ordenadas, captura de IDs, casos normales/errores y limpieza de los perfiles creados. [datos-prueba.postman.json](datos-prueba.postman.json) permite ejecutar dos iteraciones con datos diferentes. Las instrucciones y los límites de las pruebas de roles están en [docs/PRUEBAS-POSTMAN.md](docs/PRUEBAS-POSTMAN.md).
