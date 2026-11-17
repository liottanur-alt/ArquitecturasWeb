package org.msvcompraentradasol.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservaResponseDTO(
        Long id,
        BigDecimal precioUnitario,
        Instant venceEn,
        String estado,
        List<Long> idAsientos
) {
}
