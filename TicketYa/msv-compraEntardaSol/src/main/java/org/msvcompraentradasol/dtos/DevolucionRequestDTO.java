package org.msvcompraentradasol.dtos;

import java.time.Instant;
import java.util.List;

public record DevolucionRequestDTO(
        String claveIdempotencia,
        Integer cantidad,
        Instant solicitadaEn,
        List<Long> idAsientos
) {
}
