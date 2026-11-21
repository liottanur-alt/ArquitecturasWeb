# TicketYa



Sistema de eventos y venta de entradas con cinco aplicaciones, MongoDB para Usuarios y MySQL para Eventos, Sectores y Compras. Cada servicio accede solo a su propia base.

Para empezar, abrir Docker Desktop y ejecutar desde esta carpeta:

```powershell
docker compose up --build -d --wait --wait-timeout 600
```

El Gateway queda en http://localhost:8080. Pagos y reintegros son simulados. Usuarios emite JWT y el Gateway y los cuatro microservicios protegidos validan localmente el mismo HS256, secreto e issuer; `/auth/login` y `/auth/registro` son públicos y el resto de las rutas sigue la [política del Gateway](api-gateway/README.md). Las llamadas Feign internas usan un JWT de servicio con `ROLE_SERVICIO_INTERNO` y las rutas con segmento `interno` no son accesibles desde el Gateway. Antes de arrancar, copiar `.env.example` a `.env` y completar `JWT_SECRET` y `ADMIN_SEED_PASSWORD` (ver [Docker](DOCKER.md)).

- [Arranque rápido e IntelliJ](GUIA-ARRANQUE.md)
- [Comandos y archivos Docker](DOCKER.md)
- [Arquitectura y puertos](Guia.md)
- [Esquemas, credenciales y migración](docker/mysql/README.md)
- [Ejecutar las pruebas](tests/system/README.md)
- [Resultados verificados](tests/system/RESULTADOS.md)
- [Mejoras pendientes](MEJORAS-PENDIENTES.md)
