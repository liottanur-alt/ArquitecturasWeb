package com.msvnotificaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record LogRequest(@NotBlank @Size(max = 80) String servicio,
                         @NotBlank @Size(max = 15) String nivel,
                         @NotBlank @Size(max = 2000) String mensaje,
                         String idCorrelacion, Instant fecha) {}
