package org.msvcompraentradasol.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.msvcompraentradasol.dtos.CompraRequestDTO;
import org.msvcompraentradasol.dtos.EventoFechaResponseDTO;
import org.msvcompraentradasol.dtos.ReservaResponseDTO;

/** Datos compartidos por los tests para no repetir armado de DTOs. */
public final class TestFixtures {

    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private TestFixtures() {
    }

    public static EventoFechaResponseDTO eventoFuturo(Long id) {
        return eventoEn(id, LocalDateTime.now(ZONA).plusDays(30));
    }

    public static EventoFechaResponseDTO eventoEn(Long id, LocalDateTime fechaHora) {
        return new EventoFechaResponseDTO(
                id,
                fechaHora,
                "ACTIVO",
                "PUBLICADO",
                new EventoFechaResponseDTO.LugarDTO(9L)
        );
    }

    public static ReservaResponseDTO reservaActiva(Long id, String precio, List<Long> asientos) {
        return reserva(id, precio, "ACTIVA", asientos);
    }

    public static ReservaResponseDTO reserva(
            Long id,
            String precio,
            String estado,
            List<Long> asientos
    ) {
        return new ReservaResponseDTO(
                id,
                new BigDecimal(precio),
                Instant.now().plusSeconds(900),
                estado,
                asientos
        );
    }

    public static CompraRequestDTO compraConAsientos(
            String clave,
            Long idCliente,
            Long idEvento,
            List<Long> asientos
    ) {
        CompraRequestDTO dto = base(clave, idCliente, idEvento);
        dto.setIdsAsientos(asientos);
        dto.setCantidadEntradas(asientos.size());
        return dto;
    }

    public static CompraRequestDTO compraSinAsientos(
            String clave,
            Long idCliente,
            Long idEvento,
            int cantidad
    ) {
        CompraRequestDTO dto = base(clave, idCliente, idEvento);
        dto.setCantidadEntradas(cantidad);
        return dto;
    }

    private static CompraRequestDTO base(String clave, Long idCliente, Long idEvento) {
        CompraRequestDTO dto = new CompraRequestDTO();
        dto.setClaveIdempotencia(clave);
        dto.setIdCliente(idCliente);
        dto.setIdEvento(idEvento);
        dto.setIdSector(3L);
        dto.setMedioDePago("TARJETA");
        return dto;
    }
}
