# Recomendaciones: correccion y rendimiento HU10

Objetivo acordado: 100 consultas de recomendaciones sobre un catalogo de 10.000 eventos, con p95 <= 2.000 ms en el entorno local. Se miden solicitudes secuenciales completas a traves de Gateway, despues de 10 consultas de calentamiento. La validacion de las respuestas se realiza fuera del cronometro.

Con Docker Desktop y el sistema principal saludable, desde la raiz:

```powershell
docker compose build eventos usuarios sectores gateway
./tests/recomendaciones/Test-HU10-Rendimiento.ps1
```

El ejecutor crea bases MySQL y MongoDB y una cuenta MySQL exclusivas del ensayo. Levanta cuatro contenedores temporales (Eventos, Sectores, Usuarios y Gateway) con las imagenes locales; comparte los servidores MySQL/MongoDB y consulta Compras real con un ID de cliente alto cuyo historial se comprueba vacio. No usa proveedores simulados ni modifica el catalogo principal. En finally retira sus contenedores, bases y cuenta. No borra volumenes del sistema.

El conjunto sintetico contiene 10.000 eventos publicados y futuros, 60 candidatos por preferencias con coincidencias independientes por artista, tipo o ciudad, y tres candidatos con sus cupos retenidos por reservas reales. El evento con mejor puntuacion tiene el ID 10.000 para detectar limites anticipados. Se comprueban IDs, datos, puntos, motivos y orden de los diez resultados frente al baseline independiente construido con las reglas anteriores y el mismo conjunto. El ejecutor lee una vez el catalogo completo para comprobar el conjunto; Usuarios nunca lo descarga.

Se validan la busqueda OR y el lote de IDs, y se comprueba que historial e inventario esten verificados, sin respuesta parcial. Las pruebas Java y el recorrido real `msv-usuario/scripts/Test-ComprasEnComun.ps1 -TestInventario -TestOutage` verifican adicionalmente las compras en comun, exclusiones, reintegros y degradacion de dependencias.

Resultado en `.local/system-tests/hu10-performance-result.json`: p50, p95, maximo, objetivo, cantidad de eventos/candidatos, resultados, coincidencia con baseline y limpieza. La carga es local, selectiva y con concurrencia 1; no representa una garantia para todos los eventos coincidiendo, clientes con historiales grandes, carga concurrente, red de produccion o arranque en frio. Las mediciones incluyen proveedores reales y sus bases, y dependen de los recursos de Docker disponibles.

Medicion final verificada el 16/11/2026: p50 271,42 ms, p95 339,73 ms, maximo 399,78 ms. 100 consultas correctas, 60 candidatos, tres agotados excluidos y diez resultados por consulta coincidentes con el baseline. `passed=true`, `baselineCoincidente=true` y `cleanupPassed=true`. Se cumple el objetivo acordado de p95 <= 2.000 ms.
