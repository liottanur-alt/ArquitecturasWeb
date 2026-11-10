# MySQL compartido de TicketYa

## Arranque

Con Docker Desktop iniciado, desde la raiz TicketYa:

```powershell
docker compose up -d --wait
docker compose ps
.\scripts\Verificar-Bases.ps1
```

El proyecto se llama ticketya-db. Levanta **un servicio mysql:8.4**, con un volumen persistente llamado ticketya_mysql_data y tres esquemas. El puerto del equipo es 3306, accesible solo desde localhost.

| Esquema | Cuenta | Clave local por defecto | Permisos |
| --- | --- | --- | --- |
| usuarios | usuario_app | usuario_dev | Solo usuarios.* |
| eventos | evento_app | evento_dev | Solo eventos.* |
| sectores_asientos | sector_app | sector_dev | Solo sectores_asientos.* |

La cuenta root utiliza MYSQL_ROOT_PASSWORD (por defecto root_ticketya_dev). Las aplicaciones no usan root. El healthcheck conecta con cada cuenta a su esquema; no declara healthy hasta que las tres funcionan. Verificar-Bases.ps1 comprueba adicionalmente que los seis accesos entre cuentas y esquemas ajenos fallen con el error de permisos esperado.

Los scripts de init se ejecutan solo al crear el volumen vacio. Crear las bases no crea las tablas de negocio: Hibernate las crea/actualiza al arrancar cada microservicio. Los datos se conservan al ejecutar docker compose stop o down sin eliminar volumenes.

## Conexiones de las aplicaciones

Los application.properties ya apuntan a localhost:3306, con bases y cuentas separadas. Los puertos HTTP siguen iguales: Usuarios 8004, Eventos 8005 y SectorAsiento 8002.

Si mas adelante las aplicaciones se ejecutan en la red del mismo Compose, deben usar mysql:3306 como host. Por ahora se ejecutan desde IntelliJ o PowerShell en el equipo.

.env.mysql.example muestra las variables configurables. Se puede copiar a .env para que Compose lo lea. Las variables .env de Compose no se cargan automaticamente en las aplicaciones Java: si se cambian contrasenas, definir USUARIO_DB_PASSWORD, EVENTO_DB_PASSWORD o SECTOR_DB_PASSWORD tambien en sus terminales/configuraciones de IntelliJ. DB_PASSWORD sigue admitido como valor comun alternativo por compatibilidad, pero se prefieren las variables especificas.

MYSQL_PORT cambia el puerto publicado. Si 3306 esta ocupado, por ejemplo:

```powershell
$env:MYSQL_PORT='3311'
docker compose up -d --wait
```

En ese caso, establecer DB_URL en cada aplicacion, cambiando solo el esquema:

```properties
jdbc:mysql://localhost:3311/usuarios
jdbc:mysql://localhost:3311/eventos
jdbc:mysql://localhost:3311/sectores_asientos
```

Editar passwords en .env no modifica las cuentas ya guardadas en un volumen inicializado. Una rotacion requiere cambiar las credenciales en MySQL y en las aplicaciones de forma coordinada; no borrar el volumen para resolverlo.

Los scripts .ps1 requieren PowerShell 7.3 o superior para conservar correctamente los argumentos que se envían a Docker. Los comandos Compose pueden ejecutarse desde una terminal normal.

## Conservar los datos anteriores

El nuevo volumen es independiente de los volumenes anteriores. **No se eliminaron ni se migraron datos automaticamente.** Si ya habia registros, migrar antes de iniciar Spring Boot contra las nuevas bases:

1. Detener las aplicaciones Java para que no escriban ni creen tablas durante la migracion.
2. Iniciar Docker Desktop. Dejar disponibles los contenedores anteriores; el script puede iniciar los que esten detenidos.
3. Desde la raiz, ejecutar:

```powershell
.\scripts\Migrar-Bases.ps1 -DetenerOrigenes
```

El script detecta los contenedores antiguos por las etiquetas de servicio mysql-usuario, mysql-evento y mysql-sector. Inicia la base compartida, rechaza cualquier esquema destino que ya tenga tablas, respalda todos los origenes detectados antes de importar, importa cada esquema y compara cantidad de tablas y filas. Solo con -DetenerOrigenes detiene los contenedores antiguos al terminar correctamente. No elimina sus volumenes ni los archivos SQL.

Los respaldos se guardan en .local/backups/mysql-<fecha>-<id>/ con un manifiesto SHA-256. Esa carpeta esta excluida de Git. Los dumps se copian como archivos con docker cp para evitar alterar su codificacion mediante pipelines de PowerShell. Contienen datos de las aplicaciones: conservarlos localmente para poder recuperar el origen.

Si hay varias coincidencias, indicar nombres concretos:

```powershell
.\scripts\Migrar-Bases.ps1 -ContenedorUsuarios 'nombre-real-usuarios' -ContenedorEventos 'nombre-real-eventos' -ContenedorSectores 'nombre-real-sectores' -DetenerOrigenes
```

Los nombres se consultan con docker ps -a. No es obligatorio tener los tres origenes: una base sin contenedor detectado queda vacia para que Spring la inicialice. Si solo quedaron volumenes antiguos pero no sus contenedores, el script no los detecta: hay que recuperar esos contenedores antes, sin reinicializar ni borrar los volumenes.

La importacion no es una transaccion unica entre esquemas. Si falla a mitad, los origenes y respaldos se conservan; revisar el destino antes de reintentar. El script rechaza sobrescribir esquemas con tablas, incluso tras una importacion parcial. La comparacion de filas supone que las aplicaciones origen estuvieron detenidas durante todo el proceso.

## Compatibilidad con comandos anteriores

Los Compose dentro de msv-usuario, msv-evento y msv-SectoerAsiento ahora incluyen el Compose raiz y usan el mismo nombre de proyecto. Por eso levantan la misma base compartida, no otros tres contenedores.

Los Compose originales estan en docker/mysql/legacy como referencia. No usarlos para el arranque habitual: describen la topologia vieja con puertos 3307/3308/3310. Si los contenedores antiguos ya estaban ejecutandose, siguen siendo recursos independientes hasta migrarlos y detenerlos; levantar el nuevo Compose no los borra ni los detiene por su cuenta.

## Administracion

Desde la raiz:

```powershell
docker compose logs --tail 50 mysql
docker compose exec mysql mysql -u usuario_app -p usuarios
docker compose exec mysql mysql -u evento_app -p eventos
docker compose exec mysql mysql -u sector_app -p sectores_asientos
docker compose stop
```

La contrasena se solicita al abrir cada consola. Las tres bases estan en un solo contenedor, pero siguen separadas: no hay joins, claves foraneas ni entidades JPA entre microservicios.

## Verificación de esta configuración

Se validó Compose para la raíz y los tres archivos de compatibilidad: todos resuelven al mismo proyecto ticketya-db, un servicio mysql y el volumen ticketya_mysql_data. Se validó la sintaxis Bash/PowerShell y pasaron las 15 pruebas de Usuarios y 37 de SectorAsiento con H2. Docker Desktop no estaba iniciado: el arranque, los permisos reales y la migración quedan preparados para verificar con los comandos de esta guía; no se ejecutaron sobre datos existentes.

Si IntelliJ conserva DB_URL con los antiguos puertos 3307, 3308 o 3310, quitar esa variable para usar el valor por defecto nuevo o actualizarla a localhost:3306 con el esquema correspondiente. Reiniciar las aplicaciones después de cambiar sus variables.
