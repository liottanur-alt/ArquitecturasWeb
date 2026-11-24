# Inicio de sesión y registro

`/registro/` crea una cuenta CLIENTE con `POST /auth/registro`. El formulario envía nombre, email, DNI, ciudad y contraseña. El servidor asigna el rol. Tras confirmar el alta, la pantalla lleva a `/login/` conservando el destino interno.

`/login/` envía email y contraseña a `POST /auth/login`. Al recibir el JWT, el frontend guarda el token en el almacenamiento de la pestaña y vuelve al destino interno indicado por `volver`. Si el formulario se abre sin ese parámetro, dirige a `/perfil/`. El menú Mi cuenta permite cerrar sesión y conservar el carrito local asociado a la cuenta.

El cliente HTTP agrega Bearer a las peticiones protegidas del Gateway. El servidor valida identidad y permisos. Un 401 en una ruta protegida dirige a login. Un 401 al iniciar sesión muestra el error de credenciales en el formulario. Ante 403 se conserva la sesión.

Para ver el arranque local y preparar cuentas de prueba, consultar la [documentación del sistema](../DOCUMENTACION-TICKETYA.md).
