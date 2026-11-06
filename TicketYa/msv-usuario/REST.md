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
| GET | /usuarios/{id}/artistas-favoritos | Lista de IDs de artistas |
| PUT | /usuarios/{id}/artistas-favoritos/{idArtista} | Agrega un favorito (204, sin body) |
| DELETE | /usuarios/{id}/artistas-favoritos/{idArtista} | Quita un favorito (204) |

El ID del artista debe ser positivo. Este servicio guarda la referencia externa;
no consulta ni valida su existencia en el microservicio de artistas.

## Errores

- 400: campos obligatorios ausentes o invalidos, o JSON incorrecto.
- 404: usuario o rol inexistente.
- 409: email, DNI o nombre de rol duplicado; rol en uso; otra restriccion de integridad.

## Autenticacion JWT

`POST /auth/registro` es publico. Crea un CLIENTE y devuelve su perfil (201),
sin password ni hash. No acepta la asignacion de roles desde el registro.

```json
{"nombre":"Ana","email":"ana@example.com","dni":"12345678","ciudad":"Tandil","password":"1234"}
```

`POST /auth/login` es publico:

```json
{"email":"ana@example.com","password":"1234"}
```

Devuelve un access token firmado, valido por 1800 segundos:

```json
{"accessToken":"<JWT>","tokenType":"Bearer","expiresIn":1800}
```

Usar `Authorization: Bearer <JWT>` en las rutas protegidas. Credenciales invalidas
o token invalido/vencido devuelven 401; permisos insuficientes devuelven 403.
No se implementa refresh token: al vencer, iniciar sesion nuevamente.

Solo ADMINISTRADOR lista todos los usuarios, crea cuentas por el CRUD y administra
roles. Cada usuario puede consultar, actualizar o eliminar su propia cuenta y
gestionar sus favoritos; no puede cambiar su rol. ADMINISTRADOR puede gestionar
cualquier cuenta. Las cuentas creadas mediante el CRUD anterior no reciben password
y no pueden iniciar sesion; usar `/auth/registro` para cuentas con credenciales.

Los roles se inicializan automaticamente. El perfil local `dev` (predeterminado)
crea `admin@admin.com` con password `1234`, guardada como hash BCrypt. No se cambia
la password si esa cuenta ya existe. Otros perfiles no crean ese administrador
salvo que se active explicitamente `app.seed-admin=true`.

## Verificacion

`mvnw.cmd -Dtest=UsuarioServiceTests test` ejecuta pruebas unitarias sin MySQL.
Los tests usan el perfil test con una base H2 aislada; no requieren MySQL ni Docker.
