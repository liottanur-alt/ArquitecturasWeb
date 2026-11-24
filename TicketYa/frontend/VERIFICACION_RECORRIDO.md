# Verificación del recorrido integrado

## Casos de cierre de la integración

- **Reserva vencida.** La compra de prueba 109 quedó `PENDIENTE` con el plazo local de diez minutos. Después del vencimiento, Edge retomó la compra y solicitó el pago simulado. El Gateway devolvió HTTP `200` con estado `EXPIRADA`, la interfaz mostró el vencimiento y la consulta de entradas devolvió una lista vacía.
- **Sesión vencida.** Edge inició sesión contra el Gateway real. Se adelantó el reloj solo en esa página hasta después del `exp` del JWT. El frontend borró la sesión y redirigió al login conservando el destino `/perfil/#carrito` con su query. El reloj del Gateway quedó intacto.
- **Espera en la fila real.** En el evento de prueba 16, Edge mostró la posición 1 y mantuvo bloqueada la compra. Cuando se aprobó la compra de otro cliente admitido, Eventos liberó su turno y Edge habilitó la compra en la siguiente consulta.
- **Build estático.** Un servidor HTTP temporal sirvió el contenido de `dist/` y el reverse proxy `/gateway` al mismo origen. Las ocho rutas respondieron y Edge inició sesión y cargó el catálogo con JWT.
- **Pruebas funcionales del Gateway.** `tests/system/Test-System.ps1` completó 10 comprobaciones, incluidas la admisión del siguiente cliente en Redis, el pago simulado, la devolución y la restitución del inventario.

TypeScript, las 77 pruebas del frontend y el recorrido simulado volvieron a pasar. El build generó ocho páginas. Los datos de prueba quedaron en el entorno local.

## Pantallas operativas en Edge · 24 de noviembre de 2026

Los cuatro roles iniciaron sesión contra el Gateway local. El menú mostró las operaciones correspondientes a cada rol. Las acciones siguientes usaron datos de prueba.

| Pantalla | Resultado observado |
| --- | --- |
| `/organizador/` | Se consultó el reporte del evento 15. Desde la interfaz se crearon un lugar, un artista, un borrador, un sector numerado y su asiento. Se configuró el inventario y se publicó el evento 17. |
| `/administracion/` | Se aceptó la solicitud 100 y se rechazó la 101. El panel mostró el resultado confirmado por el Gateway. |
| `/acceso/` | Se consultó el QR de la entrada 107 y se registró el ingreso. Al consultarlo otra vez, la pantalla mostró que ya estaba usado y deshabilitó la confirmación. Se probó el ingreso del código como texto. |

Los registros de prueba quedaron en el entorno local.

## Edge con el Gateway local · 24 de noviembre de 2026

Docker Desktop inició los contenedores existentes y el Gateway respondió `UP`. Edge ejecutó el frontend de Astro con el proxy `/gateway`. Las solicitudes de catálogo enviaron el JWT al Gateway y no hubo llamadas del frontend a `/reservas`.

| Escenario | Resultado observado |
| --- | --- |
| Registro, login y catálogo | Registro `201`, login `200` y catálogo autenticado `200`. |
| CAMPO sin fila | En el evento de prueba 15, la compra 106 se retomó desde el historial y el pago simulado quedó `APROBADA`. Se emitió una entrada y el navegador renderizó el QR. |
| Devolución | Se creó desde la entrada la solicitud 101 y la interfaz mostró `PENDIENTE`. |
| NUMERADO con fila | En el evento de prueba 16, el Gateway devolvió un turno `ADMITIDO`. El checkout envió el asiento físico y la autorización del turno. |
| Respuesta de compra perdida | El navegador descartó deliberadamente la respuesta de creación de la compra 107 después de recibirla el servidor. El botón de recuperación reenvió el mismo pedido con la misma clave y obtuvo el mismo ID. El pago simulado quedó `RECHAZADA`. |

Se conservaron las cuentas, eventos, compras y solicitudes de prueba en el entorno local. La espera y el vencimiento del turno se comprobaron con el simulador. El vencimiento real de una reserva y de una sesión quedaron fuera de esta ejecución en Edge.

## Integración con fila virtual · 24 de noviembre de 2026

`npm run check`, las 70 pruebas de `npm test` y `npm run test:journey` pasaron. El último comando compiló las cinco páginas estáticas y ejecutó dos recorridos con API en memoria.

- CAMPO sin demanda. El usuario inicia sesión, guarda la selección, crea una compra, la retoma desde el historial, simula el pago, muestra el QR y solicita devolución. El navegador simulado no consulta fila ni reservas internas.
- NUMERADO con demanda. El usuario elige un asiento físico, espera, ve vencer el turno y vuelve a ingresar. Tras la admisión, el intento guarda la autorización con el pedido y la clave de idempotencia. Al perderse la respuesta del POST, la recuperación reenvía el mismo cuerpo. Cerrar el diálogo durante la espera detiene las consultas.

Durante esta primera ejecución el motor Linux de Docker Desktop todavía no estaba disponible. Los recorridos simulados no crearon datos en el backend.

## Verificación histórica del recorrido de demostración

Fecha: 17 de noviembre de 2026. Todos los cambios de esta revisión están en `frontend/`.

## Resultados

| Comprobación | Resultado |
| --- | --- |
| `npm run check` | Correcto: TypeScript, sin emitir archivos |
| `npm test` | 66 pruebas aprobadas |
| `npm run test:journey` | Correcto: compila y ejecuta el recorrido integrado en DOM simulado |
| `npm run build` | Correcto: 5 páginas estáticas |

Ejecutar desde `frontend/`, con las dependencias instaladas y una versión de Node compatible con `package.json`:

```powershell
npm ci
$env:ASTRO_TELEMETRY_DISABLED = '1'
npm run check
npm test
npm run test:journey
npm run build
```

`test:journey` usa el HTML generado por Astro, los controladores reales del frontend, almacenamiento en memoria, LinkeDOM y la biblioteca real de QR. Su API está sustituida completamente por funciones en memoria: no usa red y no crea recursos en ningún backend. No equivale a una prueba en un navegador real.

## Recorrido comprobado

1. Leer perfiles, seleccionar explícitamente un ID y conservar una redirección interna. La selección no autentica.
2. Cargar catálogo realista mediante el contrato de búsqueda y abrir un evento por su ID, sin eventos de ejemplo del producto.
3. Leer detalle y disponibilidad; elegir cantidad CAMPO de un sector.
4. Guardar en el carrito del perfil sin reservar. Abrir el carrito, contrastarlo con disponibilidad y recuperar la selección en el detalle.
5. Crear una compra desde esa selección: el intento queda persistido antes del POST, no envía precios y no crea una reserva independiente.
6. Consultar la reserva vinculada y mostrar su estado ACTIVA.
7. Aprobar el pago simulado, mostrar APROBADA y consultar entradas de esa compra.
8. Mostrar la compra en el historial y navegar a sus entradas, verificando el perfil propietario.
9. Resolver evento y sector, reconsultar la entrada y generar un PNG QR con la cadena exacta del servidor.
10. Solicitar devolución por una entrada; consultar solicitudes y entradas otra vez. Mostrar PENDIENTE y SOLICITUD_DEVOLUCION, con QR deshabilitado para ese estado.

El recorrido comprueba que solo hay un POST de compra y uno de solicitud, que el servidor simulado no recibe `/uso` y que no se envía POST `/reservas`. Los datos de esta prueba son fixtures exclusivamente de test.

La suite adicional cubre selección NUMERADO con IDs físicos, inventario cambiado, aislamiento entre perfiles, almacenamiento inválido, duplicados, timeout con conservación de clave y cuerpo, recuperación de compra conocida, reservas vencidas, pago RECHAZADA/EXPIRADA con respuesta exitosa, bloqueo de decisiones incompatibles, filtros y respuestas atrasadas, preferencias completas, artistas favoritos, recomendaciones parciales, estados de entrada/QR y reglas de devolución de 72 horas. Incluye regresiones de las correcciones siguientes.

## Correcciones realizadas

- El botón de rechazo de un pago retomado ahora exige la misma verificación reciente de reserva que el de aprobación. Una reserva ACTIVA guardada de una lectura anterior no habilita pagos si la consulta actual falló.
- Una compra APROBADA con reserva CONFIRMADA permite preparar explícitamente un intento nuevo, por ejemplo para otro sector. No se reutiliza la clave anterior. Los resultados inciertos siguen bloqueando el reemplazo del intento.
- Se corrigió el tipo del ID de perfil capturado por las funciones asíncronas de entradas: las tres comprobaciones que fallaban ahora reciben un ID no nulo.
- Se agregaron scripts de pruebas y tipos, dependencias de desarrollo y un recorrido integrado reproducible.

## Lecturas intentadas contra el backend

Destino configurado: `http://localhost:8080`.

| Método y ruta | Resultado |
| --- | --- |
| GET `/actuator/health` | Conexión rechazada (`ECONNREFUSED`) |
| GET `/api/eventos` | Conexión rechazada (`ECONNREFUSED`) |
| GET `/usuarios` | Conexión rechazada (`ECONNREFUSED`) |
| GET `/roles` | Conexión rechazada (`ECONNREFUSED`) |

El primer intento dentro del entorno restringido devolvió EACCES. Se repitieron las mismas lecturas con acceso de red autorizado y se obtuvo ECONNREFUSED. Esto acredita que no se pudo conectar al Gateway desde esta sesión; no permite evaluar los servicios internos por separado. No se iniciaron contenedores ni se modificó el backend.

No se enviaron POST, PUT, PATCH ni DELETE a un backend real. Las compras, pagos y devoluciones de las pruebas ocurrieron únicamente en memoria.

## Sin verificar en esta sesión

- Datos reales y respuestas HTTP de los servicios, consistencia entre servicios, vencimiento real, reintegros y emisión real de entradas.
- CORS y transporte del proxy en un navegador conectado al Gateway, así como el reverse proxy del despliegue estático.
- Escaneo por personal de acceso, validación física del QR y resolución administrativa de devoluciones. El cliente no registra uso ni acepta/rechaza solicitudes.
- Concurrencia entre dispositivos/pestañas y cambios de inventario durante operaciones reales. Las pruebas controlan carreras y errores simulados; no prueban una transacción distribuida.
- Revisión visual y accesibilidad con navegador real, dispositivos móviles o lector de pantalla. LinkeDOM verifica estructura, controladores y transiciones, no renderizado CSS ni disposición visual.

Las restricciones documentadas del backend siguen vigentes: identidad de demostración sin autenticación, pago/reintegro simulados, selección local sin garantía de cupo y QR de ingreso no habilitado tras DEVOLUCION_RECHAZADA.
