package org.msvcompraentradasol.dtos;

public record ValidarAutorizacionFilaResponseDTO(
        boolean autorizado,
        boolean requiereFila,
        String motivo
) {}
