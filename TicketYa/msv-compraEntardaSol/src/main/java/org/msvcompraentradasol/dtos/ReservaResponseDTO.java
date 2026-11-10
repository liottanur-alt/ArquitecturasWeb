package org.msvcompraentradasol.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReservaResponseDTO(
        Long id,
        BigDecimal precioUnitario,
        Instant venceEn,
        String estado,
        List<Long> idAsientos
) {
}
