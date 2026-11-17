package com.universidad.msvsectoerasiento.dto;
import jakarta.validation.constraints.*;
import java.util.List;
/** idAsientos son IDs fisicos. Para CAMPO se envia una lista vacia. */
public record ReservaRequest(@NotBlank @Size(max=100) String claveIdempotencia,
    @NotNull @Positive Long idCliente, @NotNull @Positive Long idEvento,
    @NotNull @Positive Long idSector, @NotNull @Positive @Max(1000) Integer cantidad,
    @NotNull @Size(max=1000) List<@NotNull @Positive Long> idAsientos) {}
