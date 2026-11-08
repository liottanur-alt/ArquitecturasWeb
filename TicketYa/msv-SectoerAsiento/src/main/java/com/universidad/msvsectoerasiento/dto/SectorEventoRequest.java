package com.universidad.msvsectoerasiento.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record SectorEventoRequest(@NotNull @Positive Long idSector,
    @NotNull @DecimalMin("0.00") @Digits(integer=10, fraction=2) BigDecimal precio,
    @NotNull @Positive Integer capacidadHabilitada) {}
