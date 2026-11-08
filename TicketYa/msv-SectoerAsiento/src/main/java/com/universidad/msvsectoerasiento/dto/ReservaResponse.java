package com.universidad.msvsectoerasiento.dto;
import com.universidad.msvsectoerasiento.Entities.Reserva;
import com.universidad.msvsectoerasiento.enums.EstadoReserva;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record ReservaResponse(Long id, String claveIdempotencia, Long idCliente, Long idEvento,
    Long idSector, Integer cantidad, BigDecimal precioUnitario, Instant venceEn,
    EstadoReserva estado, Integer cantidadDevuelta, List<Long> idAsientos, List<Long> idAsientosDevueltos) {
    /** Convierte a DTO dentro de la transaccion para no exponer relaciones JPA. */
    public static ReservaResponse from(Reserva r) {
        return new ReservaResponse(r.getId(), r.getClaveIdempotencia(), r.getIdCliente(),
            r.getSectorEvento().getIdEvento(), r.getSectorEvento().getSector().getId(),
            r.getCantidad(), r.getPrecioUnitario(), r.getVenceEn(), r.getEstado(), r.getCantidadDevuelta(),
            r.getAsientos().stream().map(a -> a.getAsiento().getId()).sorted().toList(),
            r.getAsientosDevueltos().stream().map(a -> a.getAsiento().getId()).sorted().toList());
    }
}
