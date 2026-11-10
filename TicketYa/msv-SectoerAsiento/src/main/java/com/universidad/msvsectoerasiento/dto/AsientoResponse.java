package com.universidad.msvsectoerasiento.dto;

import com.universidad.msvsectoerasiento.Entities.Asiento;

public record AsientoResponse(Long id, Long idSector, String fila, String columna) {
    public static AsientoResponse from(Asiento asiento) {
        return new AsientoResponse(asiento.getId(), asiento.getSector().getId(),
                asiento.getFila(), asiento.getColumna());
    }
}
