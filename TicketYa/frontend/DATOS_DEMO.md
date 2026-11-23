# Datos de demostración cargados en el backend local

Carga realizada el 17 de noviembre de 2026 mediante el Gateway `http://localhost:8080`.
Son datos ficticios persistidos mediante endpoints reales, no contenido simulado del frontend.
No se borraron ni editaron los registros de pruebas anteriores. No hubo cobros reales.

## Cómo verlos

Abrir `http://localhost:4321`, elegir **Lucía · Demo TicketYa** (perfil 288) y recorrer las pestañas de Mi cuenta.
El selector de perfiles no autentica. Si el frontend estaba abierto, actualizar la página.

| Perfil | ID | Uso |
| --- | --- | --- |
| Lucía · Demo TicketYa | 288 | Historial, QR, devolución pendiente, recomendaciones |
| Mateo · Demo TicketYa | 289 | Otro historial y aislamiento entre perfiles |
| Producción · Demo TicketYa | 290 | Organizador de los eventos demo |

## Eventos publicados

| ID | Nombre (prefijo TicketYa Demo) | Tipo |
| --- | --- | --- |
| 6 | Noches del Río | RECITAL |
| 7 | Fuera de libreto | TEATRO |
| 8 | Copa de la Costa | DEPORTE |
| 9 | Ideas que conectan | CONFERENCIA |
| 10 | Bajo las estrellas | FESTIVAL |
| 11 | Encuentro de sabores | OTRO |

Tres lugares demo en Tandil, Buenos Aires y Mar del Plata. Cada evento tiene CAMPO y NUMERADO, con precios configurados en el inventario. Hay 12 asientos físicos por lugar, con filas A/B y columnas 1–6. Las fechas son futuras respecto de la carga. El catálogo quedó con 11 eventos publicados en total, por lo que permite probar la segunda página. Las imágenes siguen siendo ilustrativas, como establece el contrato del frontend.

## Historial de Lucía

| Compra | Estado al cargar | Qué permite ver |
| --- | --- | --- |
| 94 | APROBADA | Entrada 98 PAGA y QR del servidor |
| 95 | APROBADA | Entrada 99 SOLICITUD_DEVOLUCION y solicitud PENDIENTE |
| 96 | RECHAZADA | Pago simulado rechazado, sin entradas |
| 99 | PENDIENTE | Retomar pago simulado de platea numerada |

La reserva de la compra 99 estaba ACTIVA al verificar y vence a las **12:45:44 del 17/11/2026, hora de Buenos Aires**. Es una reserva real de diez minutos; no se renueva desde el script. Tras vencer, la UI debe indicar el estado actualizado y permitir preparar otra selección cuando corresponda.

Mateo tiene las compras aprobadas 97 y 98. Comparte un evento con Lucía y compró el festival, lo que permite observar recomendaciones por compras en común. Lucía tiene tipos preferidos y un artista favorito configurados. La consulta unificada devolvió ocho recomendaciones, sin respuesta parcial.

## Reproducción

```sh
cd frontend
node scripts/seed-demo.mjs
```

Este comando **escribe en el backend**: crea perfiles, lugares, artistas, sectores, asientos, eventos, inventario, publicaciones, compras, pagos simulados y una solicitud pendiente. Sólo usar contra un entorno de demostración. El destino predeterminado es localhost:8080; se puede cambiar con GATEWAY_URL.

El archivo local `.demo-data.json` guarda los IDs e intentos y está excluido de Git. El script busca sus recursos demo existentes y conserva las claves de las compras; no repite automáticamente una solicitud de devolución cuyo resultado quedó incierto. No eliminar ese archivo para repetir compras: se perderían sus claves. Una carga puede quedar parcial si falla un servicio; revisar el estado antes de repetirla. Reejecutar no renueva reservas ni cambia el resultado de pagos ya procesados.

## Verificación real realizada

- Gateway: health UP; GET de usuarios, roles, artistas, lugares y eventos respondió 200.
- Los seis eventos tienen dos sectores y sus respuestas pasaron el parser de disponibilidad del frontend.
- Historial de Lucía: cuatro compras válidas; dos entradas válidas para el contrato; una solicitud de devolución válida.
- Recomendaciones: ocho registros válidos, `parcial=false`.
- Reserva de compra 99: ACTIVA al consultar, con vencimiento recibido del servidor.

Estos resultados describen el momento de la carga; los estados pueden cambiar por uso, vencimiento o nuevas operaciones desde la aplicación.
