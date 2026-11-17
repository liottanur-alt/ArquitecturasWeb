package com.universidad.msvsectoerasiento.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AsientoRequest {
    @NotNull @Positive
    private Long idSector;
    @NotBlank @Size(max = 255)
    private String fila;
    @NotBlank @Size(max = 255)
    private String columna;
}
