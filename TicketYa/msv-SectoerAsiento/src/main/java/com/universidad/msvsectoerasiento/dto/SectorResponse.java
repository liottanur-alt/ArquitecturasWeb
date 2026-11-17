package com.universidad.msvsectoerasiento.dto;

import com.universidad.msvsectoerasiento.Entities.Sector;
import com.universidad.msvsectoerasiento.enums.TipoSector;

public record SectorResponse(Long id, Long idLugar, String nombre,
                             TipoSector tipo, Integer capacidadTotal) {
    public static SectorResponse from(Sector sector) {
        return new SectorResponse(sector.getId(), sector.getIdLugar(), sector.getNombre(),
                sector.getTipo(), sector.getCapacidadTotal());
    }
}
