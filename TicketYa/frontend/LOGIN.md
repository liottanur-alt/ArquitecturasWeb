# Login del frontend

Pantalla: `/login/`. El home y las paginas de evento muestran un acceso a la cuenta.

## Conexion local

1. Iniciar MySQL y el microservicio de usuarios en el puerto 8004 desde su rama con JWT.
2. Iniciar el API Gateway en el puerto 8080 desde su rama con las rutas de usuarios y auth.
3. Ejecutar el frontend con `npm run dev` y abrir `http://localhost:4321/login/`.

El frontend envia `POST /api/auth/login`. El proxy de desarrollo reenvia al Gateway
como `POST http://localhost:8080/auth/login`. Esto evita CORS en desarrollo y
mantiene el Gateway como punto de entrada. `GATEWAY_URL` en el entorno del proceso
permite cambiar el destino del proxy antes de iniciar Astro.

Body: `{ "email": "...", "password": "..." }`.
Respuesta esperada: `{ "accessToken": "<JWT>", "tokenType": "Bearer", "expiresIn": 1800 }`.

La cuenta de prueba del backend local es `admin@admin.com` / `1234`.
No se precarga esa clave en el formulario. El login no se simula si el servicio
no esta disponible. Si falta el backend o las rutas del Gateway en la rama actual,
se muestra un mensaje de error al intentar ingresar.

## Sesion

El access token y su vencimiento se guardan en `sessionStorage`, junto con el email
para mostrar la cuenta. No se guarda la password. La sesion visual se limpia al
vencer el token o al cerrar sesion; no hay refresh token ni revocacion del JWT en
el servidor. La validacion de firma, roles y autorizacion sigue siendo del backend.

`/login/?volver=/eventos/1/` permite volver a una ruta interna tras ingresar.
Se rechazan destinos externos. No se requieren cookies ni se envian credenciales
de otras sesiones en el request.

## Despliegue

El proxy Vite funciona durante desarrollo. En hosting estatico configurar un proxy
para `/api` hacia el Gateway, o definir `PUBLIC_API_BASE_URL` durante el build con
la URL publica del Gateway y configurar CORS alli para el origen del frontend.
No poner claves privadas o secretos JWT en variables `PUBLIC_*`.

El registro esta en `/registro/` y envia `POST /api/auth/registro`. Solicita nombre,
email, DNI (7 a 10 digitos), ciudad, password y confirmacion. Solo envia al backend
nombre, email, DNI, ciudad y password; el servidor asigna CLIENTE. Password admite
al menos 4 caracteres y hasta 72 bytes, en linea con el ejemplo de backend.
Al confirmar el alta, vuelve al login con un aviso; no inicia sesion automaticamente.
Los errores de duplicados (409), datos invalidos (400) y conexion se muestran en pantalla.
La recuperacion de password todavia no tiene pantalla. El token no convierte la seleccion de entradas del boceto en una reserva real.
