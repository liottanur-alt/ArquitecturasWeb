# Diseno del inventario

## Modulos y responsabilidades

```mermaid
flowchart LR
    C[Cliente] --> G[API Gateway]
    G --> S[SectorAsiento]
    S --> DB[(MySQL propio)]
    S -->|REST Feign: validar evento| E[Eventos]
    CO[Compras: orquestador] -->|reservar / confirmar / liberar / devolver| S
    CO --> P[Pasarela de pagos mock]
    CO --> Q[Emision de entradas QR]
    E -->|cancelacion a coordinar por el proveedor| S
```

El gateway y las rutas de inventario estan implementados. Los proveedores Eventos y Compras todavia deben implementar y acordar sus contratos. No se comparten entidades JPA entre servicios: idLugar, idCliente e idEvento son referencias externas. EventoInventario replica lugar/fecha del proveedor para coordinar su inventario; Eventos sigue siendo la fuente de verdad y se consulta por Feign antes de vender/confirmar y al devolver.

## Modelo local

```mermaid
erDiagram
    SECTOR ||--o{ ASIENTO : contiene
    SECTOR ||--o{ SECTOR_EVENTO : configura
    INVENTARIO_EVENTO ||--o{ SECTOR_EVENTO : agrupa
    SECTOR_EVENTO ||--o{ ASIENTO_EVENTO : habilita
    ASIENTO ||--o{ ASIENTO_EVENTO : participa
    SECTOR_EVENTO ||--o{ RESERVA : recibe
    RESERVA }o--o{ ASIENTO_EVENTO : lugares_originales
    RESERVA }o--o{ ASIENTO_EVENTO : lugares_devueltos
    RESERVA ||--o{ DEVOLUCION : registra
```

La relacion entre InventarioEvento y SectorEvento se mantiene por idEvento en el servicio; no hay una relacion JPA adicional. Las tablas de union conservan los lugares originales y devueltos. Se impone unicidad por sector/fila/columna y por configuracion/asiento. La reserva captura el precio para que una compra no cambie de importe por modificaciones posteriores. Los contadores de campo y de sectores numerados se actualizan junto con la reserva; para numerados se actualizan tambien sus posiciones.

## Secuencia de compra numerada

```mermaid
sequenceDiagram
    participant C as Compras
    participant S as SectorAsiento
    participant D as Base de datos
    participant E as Eventos
    participant P as Pagos mock
    C->>S: POST /reservas (clave, cliente, evento, sector, asientos)
    S->>D: Iniciar transaccion / bloquear InventarioEvento
    S->>D: Recuperar clave o validar cupo, limite y lugares
    S->>E: Verificar evento publicado y vigente
    S->>D: Retener lugares + guardar reserva con TTL
    S-->>C: Reserva ACTIVA
    C->>P: Procesar pago idempotente
    alt Pago rechazado
        C->>S: POST /reservas/id/liberacion
        S->>D: Bloquear evento / liberar / marcar LIBERADA
        S-->>C: Reserva LIBERADA
    else Pago aprobado
        C->>S: POST /reservas/id/confirmacion
        S->>D: Bloquear evento / verificar vencimiento
        alt Reserva vigente
            S->>E: Verificar publicacion y vigencia
            S->>D: Cambiar lugares a VENDIDO / reserva CONFIRMADA
            S-->>C: CONFIRMADA
            Note over C: Emitir QR una sola vez
        else Reserva vencida
            S->>D: Liberar / marcar VENCIDA / commit
            S-->>C: VENCIDA
            C->>P: Compensar pago aprobado
        end
    end
```

Ante timeout en confirmacion, Compras debe consultar la reserva antes de compensar: el commit pudo haberse completado aunque se perdiera la respuesta. Tanto confirmar como liberar soportan reintentos. La idempotencia de cobro y emision de QR pertenece a Compras; esta implementacion garantiza la parte de inventario.

## Cancelaciones y devoluciones

Una devolucion aprobada libera solo los asientos/cantidad no devueltos previamente y registra su clave. Una cancelacion bloquea el evento, libera pendientes y marca el inventario cancelado en la misma transaccion. Repetirla no cambia los contadores. No se realiza el reintegro monetario en esa transaccion: el proveedor Compras debe gestionarlo con reintentos y su propia trazabilidad.

Las pruebas ejecutadas cubren transacciones independientes con conexiones a H2, incluso en concurrencia; no lanzan distintas JVM ni comprueban MySQL. La base descartable de MySQL y los comandos estan en README.md para completar esa verificacion con Docker disponible.
