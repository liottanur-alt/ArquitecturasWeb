package org.msvcompraentradasol.dtos;

import java.math.BigDecimal;

/** Contrato de respuesta consumido por el cliente Feign de SectorAsiento. */
public record EntradaResponseDTO(
        Long id,
        String qr,
        String estadoQr,
        Long idCliente,
        Long idEvento,
        Long idSector,
        Long idAsiento,
        BigDecimal precio,
        String estadoEntrada
) {
}
