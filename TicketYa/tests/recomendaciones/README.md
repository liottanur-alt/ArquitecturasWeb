# Recomendaciones: correccion y rendimiento HU10

Objetivo acordado: 100 consultas de recomendaciones sobre un catalogo de 10.000 eventos, con p95 <= 2.000 ms en el entorno local. Se miden solicitudes secuenciales completas a traves de Gateway, despues de 10 consultas de calentamiento. La validacion de las respuestas se realiza fuera del cronometro.

Con Docker Desktop y el sistema principal saludable, desde la raiz:

```powershell
docker compose build eventos usuarios sectores gateway
./tests/recomendaciones/Test-HU10-Rendimiento.ps1
```

El ejecutor crea bases MySQL y MongoDB y una cuenta MySQL exclusivas del ensayo, identificadas por un sufijo GUID. Levanta cuatro contenedores temporales (Eventos, Sectores, Usuarios y Gateway) con las imagenes locales; comparte los servidores MySQL/MongoDB y Redis, y consulta Compras real con el ID del CLIENTE registrado, cuyo historial se comprueba vacio. No usa proveedores simulados ni modifica el catalogo principal. En finally retira sus contenedores, bases y cuenta sin `-v`; no borra volumenes del sistema.

El script genera `HU10_ADMIN_SEED_PASSWORD` temporal, pero desde Stage 3 lee `JWT_SECRET` de la configuracion raiz de Compose y lo usa como `HU10_JWT_SECRET`: los cuatro contenedores temporales y el Compras real deben compartir la misma clave HS256 para que el JWT de usuario y el JWT de servicio sean validos en todos ellos. Registra un CLIENTE con `POST /auth/registro`, obtiene su token con `POST /auth/login` y envia `Authorization: Bearer` en preferencias, favoritos, calentamiento y las 100 consultas medidas. Las comprobaciones directas contra `http://localhost:8003/interno/...` y `<Eventos>/interno/...` usan un JWT de servicio HS256 con `ROLE_SERVICIO_INTERNO` y no forman parte de la medicion por Gateway; la preparacion de reservas internas incluye ademas el claim `usuarioId` con el ID del CLIENTE registrado, exigido por el contrato de Sectores. El contenedor temporal de Eventos usa `REDIS_HOST=redis` y `REDIS_PORT=6379`, igual que el Compose principal. Las rutas `interno` siguen bloqueadas cuando se accede por Gateway.

El conjunto sintetico contiene 10.000 eventos publicados y futuros, 60 candidatos por preferencias con coincidencias independientes por artista, tipo o ciudad, y tres candidatos con sus cupos retenidos por reservas reales. El evento con mejor puntuacion tiene el ID 10.000 para detectar limites anticipados. Se comprueban IDs, datos, puntos, motivos y orden de los diez resultados frente al baseline independiente construido con las reglas anteriores y el mismo conjunto. El ejecutor lee una vez el catalogo completo para comprobar el conjunto; Usuarios nunca lo descarga.

Se validan la busqueda OR y el lote de IDs, y se comprueba que historial e inventario esten verificados, sin respuesta parcial. Las pruebas Java cubren las compras en comun, exclusiones, reintegros y degradacion de dependencias. El recorrido real `msv-usuario/scripts/Test-ComprasEnComun.ps1` ya esta adaptado al flujo autenticado y se ejecuto en sus cuatro modos mas el escenario combinado `-TestInventario -TestOutage` el 21/11/2026, incluidas las caidas de Compras, Eventos y Sectores y su recuperacion, sin modificar las reglas de autorizacion.

Resultado en `.local/system-tests/hu10-performance-result.json`: p50, p95, maximo, objetivo, cantidad de eventos/candidatos, resultados, coincidencia con baseline y limpieza. La carga es local, selectiva y con concurrencia 1; no representa una garantia para todos los eventos coincidiendo, clientes con historiales grandes, carga concurrente, red de produccion o arranque en frio. Las mediciones incluyen proveedores reales y sus bases, y dependen de los recursos de Docker disponibles.

Medicion verificada el 21/11/2026 con el flujo autenticado de Stage 3: p50 290,39 ms, p95 463,13 ms, maximo 640,18 ms. 10.000 eventos sembrados, 100 consultas correctas tras 10 de calentamiento, 60 candidatos, tres agotados excluidos y diez resultados por consulta coincidentes con el baseline. `passed=true`, `baselineCoincidente=true` y `cleanupPassed=true`. Se cumple el objetivo acordado de p95 <= 2.000 ms. El informe no incluye p99 porque el script solo calcula p50, p95 y maximo.
