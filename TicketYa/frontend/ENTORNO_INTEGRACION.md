# Entorno de integración del frontend

## Arranque completo con Docker

Con Docker Desktop activo y `.env` configurado en la raíz de TicketYa, ejecutá:

```powershell
docker compose up -d --build --wait --wait-timeout 600
```

Abrí `http://localhost:8081`. Compose compila Astro en una etapa de Node y Nginx sirve el resultado. Las solicitudes del navegador a `/gateway/*` llegan al servicio `gateway:8080` dentro de la red de Compose. El Gateway sigue disponible en `http://localhost:8080` para Postman y scripts. Podés cambiar el puerto web con `FRONTEND_PORT` en `.env`.

Para reconstruir solo la interfaz con el backend ya levantado:

```powershell
docker compose build frontend
docker compose up -d --no-deps --wait frontend
```

## Desarrollo local con recarga automática

Prepará el stack según la sección [Arranque local](../DOCUMENTACION-TICKETYA.md#1-arranque-local). Con Docker activo, el Gateway debe responder `200` y `status: UP` en `http://127.0.0.1:8080/actuator/health`.

Desde `frontend/`, con la versión de Node indicada en `package.json`:

```powershell
npm ci
$env:GATEWAY_URL = 'http://127.0.0.1:8080'
npm exec -- astro dev --background
```

El navegador usa `/gateway` como prefijo de API en el mismo origen de Astro. `GATEWAY_URL` configura el destino del proxy de desarrollo. El proxy elimina `/gateway` antes de reenviar al Gateway. La configuración predeterminada del frontend usa `/gateway` si `PUBLIC_API_BASE_URL` está ausente.

Con el puerto que informe Astro, comprobá los siguientes resultados. Los ejemplos usan el puerto predeterminado:

```powershell
curl.exe --noproxy "*" -i http://127.0.0.1:8080/actuator/health
curl.exe --noproxy "*" -i http://localhost:4321/gateway/actuator/health
curl.exe --noproxy "*" -i http://localhost:4321/gateway/api/eventos
```

Las dos consultas de salud deben devolver `200` con `UP`. La consulta de catálogo debe devolver `401` si no incluye el JWT. Una vez integrada la sesión, comprobá en el navegador que la misma ruta con sesión de cliente devuelve `200` y el catálogo. El comando `npm exec -- astro dev stop` detiene el servidor de desarrollo.

## Datos para el recorrido de prueba

En un entorno de prueba, usá la [colección de roles](../TicketYa-roles.postman_collection.json) para preparar una cuenta cliente y otra organizadora. Seguí [Publicar un evento](../DOCUMENTACION-TICKETYA.md#publicar-un-evento) para preparar estos casos y anotá los IDs que devuelve el entorno:

| Caso | Preparación |
| --- | --- |
| Compra sin fila | Evento publicado con `demanda: false` y un sector CAMPO con cupo. |
| Compra con fila | Evento publicado con `demanda: true` y un sector NUMERADO con asientos físicos disponibles. |
| Devolución | Evento publicado que admite devoluciones y cuya fecha esté a más de 72 horas. Se puede usar uno de los dos eventos anteriores si cumple las condiciones. |

## Despliegue estático

Generá `dist/` con `npm run build` y dejá `PUBLIC_API_BASE_URL` ausente o en `/gateway`. El servidor que publica los archivos estáticos debe reenviar las solicitudes `/gateway/*` al Gateway sin ese prefijo. Conservá el método, la query, el cuerpo y el header `Authorization`. Hacé coincidir esta regla antes del fallback de páginas del hosting.

Por ejemplo, si el servidor estático usa Nginx y comparte host con el Gateway:

```nginx
location /gateway/ {
    proxy_pass http://127.0.0.1:8080/;
}
```

En otro host o red, configurá como destino la dirección del Gateway accesible desde el servidor estático. Probá la salud y el catálogo desde el origen publicado con las mismas rutas `/gateway/...`.

## Estado de la primera verificación

`docker compose config --quiet` pasó. El motor Linux de Docker Desktop no estaba disponible y el puerto local 8080 rechazó la conexión.

Con un destino HTTP temporal, Astro en modo desarrollo reenvió `/gateway/actuator/health` a `/actuator/health` y devolvió el `200` del destino. También reenvió `/gateway/api/eventos?pagina=0` conservando la query y devolvió el `401` configurado en el servidor de prueba. Quedaron pendientes esas mismas solicitudes contra un Gateway activo y la creación de datos de prueba.
