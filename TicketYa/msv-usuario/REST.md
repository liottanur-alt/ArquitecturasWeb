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

## Autenticacion diferida

La version activa no requiere token ni contrasena. Crear cuentas con POST /usuarios e idRol obtenido de GET /roles. Las rutas /auth/login y /auth/registro estan retiradas y devuelven 404. El codigo JWT original, sus pruebas y configuracion estan en ../futura jwt, fuera de las fuentes que compila Maven.

Los roles siguen siendo datos del dominio; en esta etapa no restringen acceso a los endpoints. La autenticacion y autorizacion se incorporaran cuando se restaure y adapte el modulo JWT.

## Verificacion

`mvnw.cmd test` ejecuta pruebas unitarias, persistencia H2 y CRUD HTTP sin token. No requiere MySQL ni Docker.
