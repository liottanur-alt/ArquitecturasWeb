package org.msvcompraentradasol.dtos;

import java.util.List;

// Solo IDs del cliente consultado; no expone compras, entradas ni otros clientes.
public record HistorialValidoResponseDTO(List<Long> idsEventos) {
    public HistorialValidoResponseDTO { idsEventos = List.copyOf(idsEventos); }
}
