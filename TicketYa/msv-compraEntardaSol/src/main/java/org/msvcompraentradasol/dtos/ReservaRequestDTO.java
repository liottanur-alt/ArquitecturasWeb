package org.msvcompraentradasol.dtos;

import java.util.List;

public record ReservaRequestDTO(
        String claveIdempotencia,
        Long idCliente,
        Long idEvento,
        Long idSector,
        Integer cantidad,
        List<Long> idAsientos
) {
}
