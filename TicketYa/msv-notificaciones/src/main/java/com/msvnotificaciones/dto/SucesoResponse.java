package com.msvnotificaciones.dto;

import com.msvnotificaciones.entity.SucesoNegocio;
import java.time.Instant;

public record SucesoResponse(Long id, String claveIdempotencia, String tipo, Long idEvento,
                             Long idCompra, Instant fechaSuceso, Instant fechaRegistro) {
    public static SucesoResponse from(SucesoNegocio s) {
        return new SucesoResponse(s.getId(), s.getClaveIdempotencia(), s.getTipo().name(),
            s.getIdEvento(), s.getIdCompra(), s.getFechaSuceso(), s.getFechaRegistro());
    }
}
