package com.universidad.msvsectoerasiento.dto;

import com.universidad.msvsectoerasiento.enums.TipoSector;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SectorRequest {
    @NotNull @Positive
    private Long idLugar;
    @NotBlank @Size(max = 255)
    private String nombre;
    @NotNull
    private TipoSector tipo;
    @NotNull @Positive
    private Integer capacidadTotal;
}
