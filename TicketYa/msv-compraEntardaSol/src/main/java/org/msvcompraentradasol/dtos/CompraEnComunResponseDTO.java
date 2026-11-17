package org.msvcompraentradasol.dtos;

// Contrato interno con cantidades agregadas y un evento del historial propio como referencia.
public record CompraEnComunResponseDTO(Long idEvento, Long compradoresEnComun, Long idEventoReferencia) {}
