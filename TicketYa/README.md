# TicketYa



Sistema de eventos y venta de entradas con cinco aplicaciones, MongoDB para Usuarios y MySQL para Eventos, Sectores y Compras. Cada servicio accede solo a su propia base.

Para empezar, abrir Docker Desktop y ejecutar desde esta carpeta:

```powershell
docker compose up --build -d --wait --wait-timeout 600
```

El Gateway queda en http://localhost:8080. El sistema actual funciona sin autenticación; pagos y reintegros son simulados.

- [Arranque rápido e IntelliJ](GUIA-ARRANQUE.md)
- [Comandos y archivos Docker](DOCKER.md)
- [Arquitectura y puertos](Guia.md)
- [Esquemas, credenciales y migración](docker/mysql/README.md)
- [Ejecutar las pruebas](tests/system/README.md)
- [Resultados verificados](tests/system/RESULTADOS.md)
- [Mejoras pendientes](MEJORAS-PENDIENTES.md)
