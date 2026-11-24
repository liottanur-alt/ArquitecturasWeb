# Catálogo de demostración

El seed prepara 18 eventos ficticios en recintos reales de Buenos Aires, Rosario, Mendoza y Santiago del Estero. Cada fecha nueva se calcula a partir de la semana de ejecución, en horario argentino. Los precios de demostración están expresados en pesos argentinos.

Se ejecuta manualmente en un entorno local o de demostración. Desde la raíz del repositorio, con `ADMIN_SEED_PASSWORD` configurada en `.env` o en el entorno:

```powershell
docker compose up -d --build --wait
powershell.exe -NoProfile -File .\scripts\Poblar-Catalogo.ps1
```

Para un backend levantado en otra dirección, agregá `-BaseUrl http://127.0.0.1:8081`. El script usa `admin@admin.com` por defecto. Podés cambiarlo con `-AdminEmail` si tu entorno usa otro administrador.

El comando usa Windows PowerShell 5.1, incluido en Windows. Si ya estás en una sesión de Windows PowerShell 5.1 o PowerShell 7, también podés ejecutar `.\scripts\Poblar-Catalogo.ps1` directamente.

## Datos cargados

El seed crea lugares, participantes, eventos e inventarios mediante el Gateway. Registra compras simuladas, prepara devoluciones y usa las transiciones del backend para los casos de administración. Al final imprime el ID, tipo de sector, estado, precio y stock público de cada evento.

| Qué mostrar | Evento |
| --- | --- |
| Eventos de hoy y del próximo sábado | Medianoche de cuerdas y Feria de editoriales del río. La feria tiene precio cero. |
| Última entrada y agotado | La casa de las mareas y Patio Eléctrico. |
| Selección numerada y asientos vendidos | La tregua del domingo. |
| Fila virtual y compra numerada | Horizonte en vivo. Usá una cuenta con rol `CLIENTE`. |
| Borrador listo para mostrar en Organizador | Foro de diseño accesible. |
| Evento suspendido y evento cancelado | Noche de son y percusión y Encuentro de orquestas del litoral. Ambos se consultan en Organizador. |
| Devolución pendiente, aceptada, rechazada, QR usado y QR sin usar | La tregua del domingo. Los estados se reparten entre cinco entradas de una compra simulada. |

Para mostrar el historial y los códigos QR de la compra sembrada, usá la cuenta `catalogo@ticketya.invalid`. En la raíz del proyecto, este bloque copia su contraseña de demostración al portapapeles para que puedas pegarla al iniciar sesión. Usa la contraseña de administrador configurada para ese entorno y no la imprime:

```powershell
. .\tests\system\Common.ps1
$demoEmail = 'catalogo@ticketya.invalid'
$demoKey = [System.Security.Cryptography.HMACSHA256]::new(
    [Text.Encoding]::UTF8.GetBytes((Get-ValorEntorno 'ADMIN_SEED_PASSWORD')))
try {
    [BitConverter]::ToString($demoKey.ComputeHash([Text.Encoding]::UTF8.GetBytes($demoEmail))).Replace('-', '') |
        Set-Clipboard
} finally {
    $demoKey.Dispose()
}
```

En `/perfil/#entradas` podés ver los estados. Para mostrar el control de ingreso, abrí `/acceso/` con una cuenta administradora o de personal y consultá el QR todavía vigente.

Podés reenviar el script con la misma cuenta administradora y contraseña si una ejecución se interrumpe. Reutiliza los eventos reconocidos por título e imagen, retoma los borradores y evita repetir las compras ya aprobadas. Las fechas de los eventos existentes se conservan. Los cupos pueden variar si otros usuarios compran entradas.
