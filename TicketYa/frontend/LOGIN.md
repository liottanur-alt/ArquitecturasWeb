# Selección de perfil de demostración

/login/ reutiliza el diseño existente para elegir explícitamente
un perfil de GET /usuarios. No hay contraseña, JWT, login ni autenticación.
Se muestran carga, lista vacía, respuesta inválida, fallo de red y reintento.
Confirmar guarda únicamente el ID en sessionStorage y vuelve al destino interno
indicado por volver, o a /perfil/. No se aceptan destinos externos ni bucles al
selector. La selección dura mientras exista el almacenamiento de la pestaña.

Mi cuenta indica el ID y permite cambiar o quitar el perfil. Quitar no borra
el usuario del servidor ni su carrito local. Los datos de sesión JWT anteriores
se descartan. Si el servidor responde 404 al consultar el perfil, se quita la
selección y se vuelve a elegir. Un fallo temporal conserva la selección.

Seleccionar un perfil no autentica al usuario ni concede permisos.
Consultar README.md para configurar el cliente API y el proxy.

/registro/ permite crear un perfil sin contraseña. Consulta GET /roles y exige
un único CLIENTE con ID válido; no crea roles ni asume un ID. POST /usuarios
envía nombre, email, dni, ciudad e idRol. Valida campos obligatorios y límites
después de trim (100/254/20/100), formato de email y permite DNI no numérico.
Los errores 409 se muestran como posible duplicado o conflicto concurrente.
Si falta CLIENTE o falla la consulta se bloquea el alta y se ofrece reintentar.
Tras una creación confirmada no se vuelve a enviar ni se cambia la selección:
un enlace lleva al selector para elegir el perfil explícitamente. No autentica.
Tras un fallo de conexión, revisar perfiles existentes antes de volver a enviar,
ya que no hay idempotencia para crear usuarios.
