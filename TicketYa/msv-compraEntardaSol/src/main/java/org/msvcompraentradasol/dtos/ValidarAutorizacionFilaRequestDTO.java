package org.msvcompraentradasol.dtos;

public record ValidarAutorizacionFilaRequestDTO(
        Long eventoId,
        Long clienteId,
        String autorizacionCompra,
        String idOperacion
) {}
