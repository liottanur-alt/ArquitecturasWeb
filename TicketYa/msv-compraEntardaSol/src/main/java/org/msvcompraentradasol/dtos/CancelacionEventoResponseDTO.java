package org.msvcompraentradasol.dtos;

import java.math.BigDecimal;

public record CancelacionEventoResponseDTO(
        Long idEvento,
        int comprasCanceladas,
        int entradasReintegradas,
        BigDecimal importeReintegrado,
        boolean inventarioCancelado
) {
}
