# API Gateway TicketYa

Ejecutar las bases y los servicios necesarios, y despues `ApiGatewayApplication` desde IntelliJ o `.\mvnw.cmd spring-boot:run` en esta carpeta. Escucha en http://localhost:8080.

La version activa no requiere JWT. Usuarios ya no valida tokens y las rutas /auth/login y /auth/registro fueron retiradas del gateway. La implementacion original se conserva en ../futura jwt.

Rutas activas:

- /usuarios y /usuarios/**: CRUD y favoritos, destino MSVC_USUARIOS_URL (http://localhost:8004).
- /roles y /roles/**: CRUD de roles, mismo destino Usuarios.
- /sectores, /asientos, /reservas y sus subrutas: destino MSVC_SECTORES_URL (http://localhost:8002).
- /eventos/{id}/inventario y /eventos/{id}/sectores con sus subrutas: destino SectorAsiento.
- /compras, /entradas y /solicitudes con sus subrutas (incluida /compras/eventos/{id}/cancelacion): destino MSVC_COMPRAS_URL (http://localhost:8003).

Ejemplo: GET http://localhost:8080/roles no necesita Authorization. Para crear usuarios, POST /usuarios con nombre, email, dni, ciudad e idRol.

`.\mvnw.cmd test` verifica arranque, reenvio sin token, cuerpo JSON, query y propagacion de errores, usando proveedores HTTP locales de prueba. No requiere bases ni servicios externos. Incluye la ruta de Compras (`/compras`, `/entradas`, `/solicitudes`).
