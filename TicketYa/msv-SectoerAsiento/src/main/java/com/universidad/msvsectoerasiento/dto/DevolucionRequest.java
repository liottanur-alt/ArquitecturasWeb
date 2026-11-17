package com.universidad.msvsectoerasiento.dto;
import jakarta.validation.constraints.*;
import java.util.List;
import java.time.Instant;
/** Compras llama este endpoint despues de aprobar la solicitud del cliente. */
public record DevolucionRequest(@NotBlank @Size(max=100) String claveIdempotencia,
    @NotNull @Positive Integer cantidad, @NotNull Instant solicitadaEn,
    @NotNull @Size(max=1000) List<@NotNull @Positive Long> idAsientos) {}
