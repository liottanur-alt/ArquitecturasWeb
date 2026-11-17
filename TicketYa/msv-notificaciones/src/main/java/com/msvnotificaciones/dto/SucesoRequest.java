package com.msvnotificaciones.dto;

import com.msvnotificaciones.entity.TipoSuceso;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record SucesoRequest(
    @NotBlank @Size(max = 100) String claveIdempotencia,
    @NotNull TipoSuceso tipo,
    @NotNull @Positive Long idEvento,
    @Positive Long idCompra,
    @Positive Long idCliente,
    @Size(max = 320) String destinatario,
    @PositiveOrZero Integer cantidadEntradas,
    @DecimalMin("0.00") BigDecimal importe,
    @Positive Integer capacidadTotal,
    Instant fechaSuceso
) {}
