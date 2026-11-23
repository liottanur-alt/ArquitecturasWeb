# Verificación del recorrido integrado

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
