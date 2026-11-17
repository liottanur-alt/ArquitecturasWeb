# Arquitectura de TicketYa

El cliente envía solicitudes al Gateway, que las dirige al microservicio correspondiente. Los servicios se comunican por HTTP mediante Feign.

| Carpeta real | Puerto | Responsabilidad | Persistencia |
| --- | --- | --- | --- |
| api-gateway | 8080 | Recibir y dirigir solicitudes | No usa base propia |
| msv-usuario | 8004 | Usuarios, roles y favoritos | MongoDB: usuarios |
| msv-evento | 8005 | Eventos, lugares y artistas | eventos |
| msv-SectoerAsiento | 8002 | Sectores, asientos, inventario y reservas | sectores_asientos |
| msv-compraEntardaSol | 8003 | Compras, entradas y devoluciones | compra_entrada_sol |

Usuarios usa MongoDB en 27017 con colecciones usuarios, roles y secuencias. Eventos, Sectores y Compras usan esquemas propios en MySQL en 3306. Los servicios se comunican por HTTP y no consultan bases ajenas. Usuarios resuelve los IDs de favoritos llamando a GET /api/artistas/{id} de Eventos mediante Feign.

Compras coordina la reserva con SectorAsiento, el pago simulado y la emisión de entradas. La devolución aceptada reintegra el importe simulado y devuelve el cupo al inventario.

Cada aplicación tiene su Dockerfile. El Compose raíz levanta las cinco aplicaciones, MongoDB y MySQL: siete contenedores. La etapa actual funciona sin autenticación.

[Arrancar el sistema](GUIA-ARRANQUE.md) · [Configurar MySQL](docker/mysql/README.md) · [Ejecutar pruebas](tests/system/README.md)
