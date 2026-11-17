# API Gateway

Recibe solicitudes en http://localhost:8080 y las reenvía al servicio correspondiente.

| Rutas, incluidas sus subrutas | Servicio local |
| --- | --- |
| /usuarios, /roles | Usuarios: 8004 |
| /api/eventos, /api/lugares, /api/artistas | Eventos: 8005 |
| /sectores, /asientos, /reservas, /eventos/{id}/inventario, /eventos/{id}/sectores | SectorAsiento: 8002 |
| /compras, /entradas, /solicitudes | Compras: 8003 |

Para arrancar todo, seguir la [guía rápida](../GUIA-ARRANQUE.md). Desde esta carpeta, con los servicios ya disponibles:

```powershell
./mvnw.cmd spring-boot:run
```

En Linux/macOS usar `sh ./mvnw spring-boot:run`. Los destinos se configuran con MSVC_USUARIOS_URL, MSVC_EVENTOS_URL, MSVC_SECTORES_URL y MSVC_COMPRAS_URL; Compose los configura automáticamente para Docker.

No hay autenticación activa. /auth/login y /auth/registro están retirados. La exposición de rutas internas está documentada en la [auditoría](../tests/system/RESULTADOS.md).

`./mvnw.cmd test` prueba arranque y enrutamiento con proveedores HTTP simulados, sin MySQL. Las [pruebas de sistema](../tests/system/README.md) usan los servicios reales.
