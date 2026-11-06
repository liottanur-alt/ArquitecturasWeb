# API Gateway TicketYa

Ejecutar MySQL y `msv-usuario` (puerto 8004), y luego `ApiGatewayApplication`
desde IntelliJ o `mvnw.cmd spring-boot:run` en esta carpeta. El Gateway escucha
en el puerto 8080.

## Rutas para Postman

- POST `http://localhost:8080/auth/registro`: registro publico de clientes.
- POST `http://localhost:8080/auth/login`: login publico.
- GET `http://localhost:8080/usuarios`: requiere token de administrador.
- GET `http://localhost:8080/roles`: requiere token de administrador.
- GET `http://localhost:8080/usuarios/{id}`: cuenta propia o administrador.
- GET/PUT/DELETE `http://localhost:8080/usuarios/{id}/artistas-favoritos`:
  para PUT y DELETE agregar `/{idArtista}` al final.

Body de login de prueba local:

```json
{"email":"admin@admin.com","password":"1234"}
```

Usar el `accessToken` recibido en Authorization > Bearer Token de Postman.
El Gateway conserva el encabezado Authorization y la ruta completa sin quitar
prefijos. La validacion del JWT y los permisos se realizan en `msv-usuario`.
La validacion adicional en el Gateway queda para la siguiente etapa de seguridad.

`MSVC_USUARIOS_URL` configura el destino (por defecto `http://localhost:8004`).
Si el Gateway corre en Docker Desktop y usuarios en IntelliJ, usar
`http://host.docker.internal:8004`.

## Tests

`mvnw.cmd test` verifica el arranque y el reenvio HTTP contra un backend de prueba:
rutas, body JSON, query string, Bearer y propagacion de 401. No necesita MySQL
ni tener `msv-usuario` iniciado; estos tests no validan firmas JWT.
