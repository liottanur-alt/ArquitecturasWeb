# Ejecutar todo TicketYa con Docker

Requiere Docker Desktop iniciado con contenedores Linux y Docker Compose v2.20 o superior. No requiere Java ni Maven instalados en el equipo: cada Dockerfile compila con JDK 21 mediante Maven Wrapper y copia el JAR a una imagen JRE 21. La primera construcción descarga imágenes y dependencias.

Desde la raíz:

```powershell
docker compose up --build -d --wait --wait-timeout 300
docker compose ps
```

El Compose raíz inicia siete contenedores en una red privada de Compose:

| Servicio | Puerto local | Responsabilidad |
| --- | --- | --- |
| gateway | 8080 | Entrada HTTP y enrutamiento |
| usuarios | 8004 | Usuarios, roles y favoritos |
| eventos | 8005 | Catálogo y publicación |
| sectores | 8002 | Inventario, reservas y asientos |
| compras | 8003 | Compras, entradas y devoluciones |
| mysql | 3306 | Esquemas usuarios, eventos y sectores_asientos |
| mysql-compra | 3311 | Esquema compra_entrada_sol |

Abrir el Gateway en `http://localhost:8080`. Por ejemplo, `http://localhost:8080/roles` y `http://localhost:8080/api/eventos`. Los puertos se publican únicamente en la interfaz local del equipo. Se mantienen los puertos directos para las colecciones Postman existentes.

## Red, disponibilidad y datos

Feign conserva sus contratos; las variables de entorno del Compose cambian los destinos a `http://eventos:8005`, `http://sectores:8002`, `http://usuarios:8004` y `http://compras:8003`. JDBC usa `mysql:3306` o `mysql-compra:3306`; el puerto 3311 solo se utiliza desde el equipo anfitrión.

Las aplicaciones esperan una base saludable. Eventos y Sectores no esperan uno al otro al arrancar: se comunican durante las operaciones y así se evita una dependencia circular. Compras espera a Eventos y Sectores; el Gateway espera a los cuatro servicios. Los healthchecks Java consultan `/actuator/health`, incluyendo el estado de conexión a su base. `depends_on` organiza el arranque; no recupera automáticamente una operación de negocio que falle después por caída de un proveedor.

Se conserva el proyecto Compose `ticketya-db` y el volumen compartido `ticketya_mysql_data`. Compras usa el nombre de volumen de su Compose anterior, `msv-compraentardasol_compra-data`, para conservar los datos de instalaciones con ese nombre predeterminado. Si antes se usó un nombre de proyecto distinto con `-p`, revisar el volumen existente antes de arrancar.

Si ya están corriendo aplicaciones en IntelliJ, detenerlas antes de iniciar todo para liberar 8002–8005 y 8080. Si la base de Compras se inició con su Compose separado, detener ese contenedor antes de que el Compose raíz use el mismo puerto y volumen:

```powershell
docker compose -f ./msv-compraEntardaSol/compose.mysql.yml stop mysql-compra
```

No ejecutar los dos contenedores de Compras simultáneamente sobre el mismo volumen. Los Compose antiguos se conservan para compatibilidad; usar el Compose raíz para ejecutar el sistema completo.

Las contraseñas predeterminadas son las de desarrollo existentes. Opcionalmente copiar `.env.mysql.example` a `.env` antes del primer arranque. Las variables se pasan tanto a las bases como a las aplicaciones. Cambiar una contraseña en `.env` no cambia las credenciales de un volumen ya inicializado: actualizar también la cuenta MySQL correspondiente. Hibernate mantiene `ddl-auto=update` de desarrollo.

## Comandos útiles

```powershell
# Ver disponibilidad y registros
docker compose ps
docker compose logs --tail 100 eventos sectores

# Reconstruir una aplicación después de cambiar su código
docker compose up --build -d eventos

# Detener conservando datos
docker compose stop

# Eliminar contenedores y red, conservando los volúmenes
docker compose down
```

No agregar `-v` a `down` si se quieren conservar las bases.

Para seguir ejecutando Java desde IntelliJ y usar Docker solamente para las bases:

```powershell
docker compose up -d --wait mysql mysql-compra
```

## Verificación

`docker compose config --quiet` valida la configuración sin requerir el motor Docker. La prueba de construcción y arranque requiere Docker activo. Las suites automatizadas se ejecutan con Maven según los README de cada módulo; la construcción de imágenes omite las pruebas y no reemplaza esa validación.
