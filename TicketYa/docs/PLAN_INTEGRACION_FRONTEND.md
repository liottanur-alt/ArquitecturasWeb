# Plan de integración del frontend de TicketYa

El objetivo es que un cliente pueda registrarse, iniciar sesión, elegir entradas, pasar por la fila cuando corresponda, pagar en modo simulado, ver su QR y solicitar una devolución. Los cambios de implementación se hacen en `frontend/`. Los servicios Java, el Gateway y sus contratos quedan como están.

## Contratos y flujo del sistema

Usar como punto de partida la [documentación del sistema](../DOCUMENTACION-TICKETYA.md) y resolver diferencias consultando el controlador, DTO y reglas de acceso correspondientes. La [guía de integración anterior](INTEGRACION_FRONTEND.md) describe el modelo de demostración previo. El [recorrido existente](../frontend/VERIFICACION_RECORRIDO.md) usa una API simulada en memoria.

1. El organizador configura inventario y publica el evento. Para desarrollar el recorrido cliente, usar eventos publicados de un entorno de prueba.
2. Usuarios emite el JWT al hacer login. El navegador llama al Gateway por medio del proxy del frontend. El Gateway valida el token y enruta las solicitudes.
3. El comprador consulta evento y disponibilidad. Guarda una cantidad para CAMPO o IDs de asientos físicos para NUMERADO. El carrito es local y guardar una selección no crea reservas.
4. Si el evento tiene `demanda: true`, Eventos gestiona el turno. Cuando admite al cliente, el frontend obtiene la autorización y la incluye al crear la compra.
5. Compras coordina la reserva con Sectores, calcula el importe y procesa el pago simulado. El frontend consulta la compra y muestra las entradas emitidas cuando queda aprobada.
6. El comprador puede solicitar devolución por entrada. El administrador la resuelve y el personal de acceso registra el ingreso mediante QR en su propio flujo.

## Orden para asignar agentes

| Tanda | Tareas | Condición para integrar |
| --- | --- | --- |
| 1 | A y B en paralelo | Proxy verificado y contrato de sesión disponible |
| 2 | C y D en paralelo después de B | Cuenta y catálogo funcionan con JWT. Compra sin fila validada |
| 3 | E después de D | El checkout usa el turno cuando corresponde |
| 4 | F después de C y E | Recorrido de cliente comprobado de extremo a extremo |
| Posterior | G, si se requieren las pantallas operativas | Cada rol completa sus operaciones desde la interfaz |

Dar a cada agente el apartado de su tarea y el enlace a este plan. Si trabajan en paralelo, usar ramas o worktrees independientes sobre la misma base e integrar en el orden indicado. B es dueño del cliente HTTP y la sesión. C adapta sus consumidores salvo el checkout. D y E se suceden porque comparten el código de compra. F es dueño del recorrido integral. Si dos tareas necesitan tocar el mismo archivo, acordar el cambio con el agente responsable antes de editarlo.

## A. Entorno y transporte

- Revisá `frontend/astro.config.mjs`, la base `PUBLIC_API_BASE_URL` y el arranque local de la documentación. Preservá `/gateway` como ruta del mismo origen y verificá que el proxy de desarrollo elimine ese prefijo. Documentá el mapeo equivalente para servir el build estático.
- Prepará la verificación con una cuenta CLIENTE, un evento publicado con demanda y otro sin demanda, inventario CAMPO y NUMERADO, y un evento con devolución habilitada a más de 72 horas. Usá un entorno de prueba para crear datos.
- Entregá evidencia de salud directa y a través del proxy, junto con la respuesta esperada de una ruta protegida sin JWT. Si el entorno impide verificarlo, anotá el error concreto. Coordiná con B antes de editar `api.ts`.

## B. Login, registro y sesión

- Reemplazá el selector demo en `login.astro` y `login.ts` por email y contraseña contra `POST /auth/login`. Adaptá `registro.astro` y `register.ts` a `POST /auth/registro` con nombre, email, DNI, ciudad y contraseña. Tras el registro, dirigí al login conservando el retorno interno.
- En `session.ts` y `api.ts`, guardá una sesión por pestaña, obtené el ID de `sub`, gestioná la expiración y enviá Bearer al Gateway en peticiones protegidas. Reservá la validación de identidad y permisos al servidor. Una respuesta `401` de una ruta protegida pide iniciar sesión de nuevo. Una `403` informa falta de permisos. Un login fallido muestra el error en el formulario.
- Publicá las funciones de sesión que usarán C y D. Mantené coherente el contrato durante su migración y retiren juntos las referencias demo restantes.
- Aceptación. Registro y login funcionan con una cuenta CLIENTE, se recupera la ruta interna de destino y se puede cerrar sesión. El recorrido público no consulta `/roles` ni lista `/usuarios`.

## C. Catálogo, perfil y cuenta

- Migrá catálogo, detalle, carrito, perfil, preferencias, favoritos, recomendaciones, historial, entradas y devoluciones al ID de la sesión de B. Conservá el `idRol` recibido al editar el perfil. En el código compartido de detalle, coordiná con D el llamado que inicializa el checkout.
- Mantené selección CAMPO y NUMERADO, revalidación de disponibilidad y separación de datos locales por cuenta. Al cambiar de sesión, evitá renderizar resultados pendientes de la cuenta anterior. Si tocás filtros, verificá `q`, la paginación y los valores de `ordenar` contra el controlador.
- Aceptación. Dos cuentas pueden usar la misma pestaña de forma sucesiva sin mezclar borradores ni pantallas. Perfil y preferencias guardan cambios. Historial, entradas y devoluciones consultan el ID de la cuenta.

## D. Compra y pago sin fila

- Trabajá en `purchase-attempt.ts`, `purchase-ui.ts` y el armado del pedido. Enviá `idCliente` de la sesión, evento, sector, medio de pago y la selección correspondiente. Persistí el cuerpo y la `claveIdempotencia` antes del POST. Si el resultado queda incierto y todavía no hay ID de compra, recuperá con el mismo cuerpo y la misma clave. Con ID conocido, consultá `GET /compras/{id}`.
- Quitá la dependencia del navegador de `GET /reservas/{id}`. Sectores requiere rol de servicio para esa ruta. La respuesta pública de compra no devuelve el vencimiento de la reserva. Usá el estado público de compra y la respuesta de `PUT /compras/{id}/pago?aprobado=...` sin body. Identificá el pago en la UI como simulado. Si se pierde su respuesta, reconciliá el estado antes de habilitar otra decisión de pago.
- Aceptación. Se compra en CAMPO y NUMERADO, se retoma una compra conocida, se identifican `APROBADA`, `RECHAZADA` y `EXPIRADA`, y solo para una compra aprobada se consultan sus entradas. Un reintento incierto no crea un segundo intento.

## E. Fila virtual

- Para eventos con demanda, usá `POST /api/eventos/{id}/fila` y consultá periódicamente `GET /api/eventos/{id}/fila/mi-turno`. En espera, mostrá la posición del servidor. En `ADMITIDO`, usá `autorizacionCompra` y `venceEn` del turno. Detené las consultas al salir del flujo, cambiar de sesión o finalizar el turno.
- Incluí la autorización en el cuerpo persistido de un intento nuevo. Si el POST de compra queda incierto, recuperá ese intento antes de reemplazar su autorización o clave. Mantené el vencimiento del turno separado del estado de compra.
- Aceptación. Un cliente espera, es admitido y compra. Se cubren turno vencido, navegación fuera de la pantalla y pérdida de respuesta al crear la compra. Los eventos sin demanda siguen el flujo de D.

## F. Verificación integrada

- Actualizá `frontend/tests/integration/journey.mjs` y las pruebas afectadas para que representen el contrato actual. Ejecutá desde `frontend/` con las dependencias instaladas y la versión de Node indicada en `package.json`.

```powershell
npm run check
npm test
npm run test:journey
npm run build
```

- Repetí en navegador contra el Gateway de prueba. Cubrí login y expiración de sesión, compra sin fila, compra con fila, respuesta de compra perdida, pago aprobado y rechazado, recuperación de compra expirada, historial, QR y devolución pendiente. Usá la colección de roles de la [documentación del sistema](../DOCUMENTACION-TICKETYA.md#crear-cuentas-y-probar-roles-en-postman) para preparar cuentas y comprobar operaciones de otros roles cuando corresponda.
- Entregá resultados separados para pruebas simuladas y navegador. Registrá qué escenarios quedaron sin verificar y su bloqueo. Las comprobaciones de pertenencia de la interfaz ayudan a evitar cruces en pantalla. La autorización efectiva de las consultas depende del backend existente.

## G. Pantallas operativas por rol

- Organizadores. Alta de lugar y artista, borrador, sectores, asientos físicos cuando apliquen, inventario, publicación y reportes del evento. Usá las rutas publicadas del Gateway y las reglas de titularidad.
- Administración. Listado de devoluciones pendientes y resolución mediante `PUT /solicitudes/{id}/resolver`. Verificá en la API el estado resultante de la solicitud y la entrada.
- Personal de acceso. Consultá el QR y registrá el ingreso mediante `PATCH /entradas/qr/{qr}/uso?idEvento=...`. Mostrá el resultado del servidor para ingreso registrado, entrada no vigente y QR ya usado.
- Aceptación. Cada pantalla usa la sesión del operador y refleja las decisiones del servidor. El reporte del organizador usa la ruta del Gateway. Los endpoints internos de Notificaciones quedan fuera de la interfaz.

## Entrega de cada agente

Devolver los archivos modificados, las pruebas ejecutadas, los resultados y las decisiones que el siguiente agente debe conocer. Si el backend no expone un dato necesario, citar el contrato actual y adaptar la interfaz a la información disponible. El cierre de la integración requiere los recorridos CAMPO y NUMERADO y la verificación en navegador de la sesión, compra, recuperación, fila y devolución.
