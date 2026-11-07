# Perfil, historial y carrito

`/perfil/` requiere una sesion local vigente. El menu Mi cuenta tiene enlaces
a Mis datos, Mis compras y Mi carrito. Sin sesion se vuelve al login.

Los datos se consultan en `GET /api/usuarios/{sub}` y se actualizan mediante
`PUT /api/usuarios/{sub}`, con el JWT en Authorization. Se mantiene el idRol
recibido del backend; no se permite seleccionar ni enviar un rol arbitrario desde el formulario.
La autorizacion efectiva la realiza el backend. Si no se pueden obtener los datos,
se informa el error sin sustituirlos por una identidad ficticia.

`/perfil/?demo=1` permite ver un perfil ficticio sin login, con una etiqueta explicita.
Sus cambios solo se guardan en sessionStorage. Cuando ya existe sesion real,
se usa el perfil autenticado y se ignora el parametro demo.

El historial es estatico y esta marcado como ejemplo para todos los usuarios.
No se consulta un servicio de compras ni se generan entradas o QR validos.

El carrito es local a este navegador y esta separado por usuario (o invitado).
Desde el resumen de un evento se puede guardar la seleccion, reemplazando la
seleccion anterior del mismo evento. En el perfil se puede quitar o continuar;
continuar restaura sectores, cantidades y asientos. Los precios se calculan desde
el catalogo de ejemplo y se validan los datos recuperados del almacenamiento.
No se reservan asientos ni se efectuan cobros. Los carritos de invitados no se
transfieren automaticamente a una cuenta al iniciar sesion.

La vista demo inicia dos selecciones solo cuando no existe un carrito de invitado.
Al vaciarlo no se vuelven a crear. No se inicia un carrito de ejemplo para usuarios reales.
Sin almacenamiento disponible se muestra un error al guardar o quitar selecciones.


## Mis entradas (demostracion)

La pestaña /perfil/#entradas muestra una entrada por lugar de las compras de
ejemplo (cinco entradas). Tambien se puede acceder desde Mi cuenta o Ver entradas
en cada compra. El filtro de compra se puede limpiar para volver a verlas todas.

Cada entrada valida genera un QR distinto con qrcode. El payload lleva el prefijo
TICKETYA-DEMO y SIN-VALIDEZ-PARA-INGRESAR; no contiene datos personales ni una firma
de acceso. Los QR de entradas utilizadas, devueltas o con devolucion pendiente no se muestran.

Las solicitudes se guardan por entrada y cuenta en localStorage (invitado en demo).
Solo se permiten para entradas validas a 72 horas o mas del inicio del evento;
se usa el timestamp absoluto con zona -03:00 y se vuelve a comprobar al confirmar.
Se impiden duplicados. El estado queda pendiente de aprobacion, sin reintegros.
Cancelar el dialogo no solicita la devolucion. Un fallo de almacenamiento no confirma
la operacion. No hay conexion con un servicio de compras, entrada o devoluciones.

El historial y sus importes siguen siendo de ejemplo; una solicitud pendiente
no se convierte en una compra devuelta ni en una aprobacion administrativa.
