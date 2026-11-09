# Microservicio de usuarios

Proyecto Spring Boot con Java 21 o superior y una base MySQL exclusiva del servicio.

## Ejecutar en desarrollo

1. Iniciar Docker Desktop.
2. Desde esta carpeta, levantar MySQL:

   ```powershell
   docker compose up -d --wait
   ```

3. Ejecutar `MsvUsuarioApplication` desde IntelliJ o usar:

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

Docker crea la base `usuarios`. Cuando arranca Spring Boot, Hibernate crea las
tablas `roles`, `usuarios` y `artistas_favoritos`. Se inicializan los cuatro roles.
El perfil predeterminado `dev` crea, si no existe, un perfil administrador de prueba sin credenciales. Los roles se inicializan automaticamente.

La version activa corresponde a la etapa sin autenticacion: todos los endpoints de usuarios, roles y favoritos funcionan sin token. Crear usuarios con `POST /usuarios`; consultar los IDs de rol con `GET /roles`. `/auth/login` y `/auth/registro` no estan activos. La implementacion JWT original se traslado a `../futura jwt`, con un manifiesto de archivos y notas de restauracion. No se borran usuarios ni datos existentes en MySQL.

El microservicio HTTP escucha en `http://localhost:8004`. Si el API Gateway se
ejecuta en el mismo equipo, su destino para las rutas `/usuarios/**` y `/roles/**`
es `http://localhost:8004`, conservando esas rutas al reenviar las solicitudes.
Si el Gateway se ejecuta en Docker Desktop y Spring Boot en el equipo, usar
`http://host.docker.internal:8004` como destino.

## Conexion a MySQL

| Dato | Valor local |
| --- | --- |
| Host | localhost |
| Puerto | 3307 |
| Base | usuarios |
| Usuario | usuario_app |
| Clave de desarrollo | usuario_dev |

El puerto 3307 del equipo apunta al 3306 del contenedor. Los valores por defecto
son para desarrollo local. Se pueden reemplazar mediante `DB_URL`, `DB_USERNAME`,
`DB_PASSWORD` y `MYSQL_ROOT_PASSWORD`. `DB_PASSWORD` debe tener el mismo valor al
levantar Compose y al ejecutar Spring Boot. Cambiar estas variables no cambia las
credenciales de una base ya inicializada en el volumen.

Si mas adelante Spring Boot se ejecuta en un contenedor conectado a la misma red,
configurar `DB_URL=jdbc:mysql://mysql-usuario:3306/usuarios`.

Cada microservicio administra su propia base. Este contenedor y su volumen son
exclusivos de usuarios; las referencias a artistas son IDs externos sin FK entre bases.

## Administrar el contenedor

```powershell
docker compose ps
docker compose logs mysql-usuario
docker compose stop
docker compose start
docker compose down
```

Los datos permanecen en el volumen `mysql_usuario_data` al detener o recrear el
contenedor, incluso con `docker compose down`. Agregar `-v` a ese comando elimina
el volumen y todos sus datos.

Para abrir una consola SQL con el usuario de la aplicacion:

```powershell
docker compose exec mysql-usuario mysql -u usuario_app -p usuarios
```

`spring.jpa.hibernate.ddl-auto=update` se usa para desarrollo. Para despliegues
con datos reales se deben versionar los cambios de esquema mediante migraciones.

Los endpoints y ejemplos de requests estan documentados en [REST.md](REST.md).

## Tests

```powershell
.\mvnw.cmd test
```

Incluyen pruebas unitarias del servicio, arranque del contexto Spring e integracion
de servicios y repositorios con H2 en modo MySQL. Verifican roles compartidos,
unicidad de email y DNI, favoritos externos sin duplicados, eliminacion de
usuarios con sus favoritos y eliminacion individual de favoritos.

El perfil `test` usa una base en memoria aislada y revierte los cambios de cada
prueba de persistencia. No requiere Docker ni modifica la base de desarrollo.
Estas pruebas no reemplazan una verificacion contra MySQL real.

Tambien se prueban por HTTP las operaciones de usuarios, roles y favoritos sin token, y se verifica que las rutas /auth retiradas respondan 404. Las pruebas originales de JWT estan archivadas en futura jwt.

## Contrato OpenAPI y colección de pruebas

El contrato OpenAPI 3.0.3 está en [src/main/resources/static/openapi.json](src/main/resources/static/openapi.json) y se sirve directamente en http://localhost:8004/openapi.json cuando Usuarios está iniciado.

Importar [TicketYa-Usuarios.postman_collection.json](TicketYa-Usuarios.postman_collection.json) en Postman. Incluye 32 solicitudes ordenadas, captura de IDs, casos normales/errores y limpieza de los perfiles creados. [datos-prueba.postman.json](datos-prueba.postman.json) permite ejecutar dos iteraciones con datos diferentes. Las instrucciones y los límites de las pruebas de roles están en [docs/PRUEBAS-POSTMAN.md](docs/PRUEBAS-POSTMAN.md).
