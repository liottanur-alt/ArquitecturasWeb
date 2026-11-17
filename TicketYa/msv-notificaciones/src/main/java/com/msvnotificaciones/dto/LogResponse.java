package com.msvnotificaciones.dto;

import com.msvnotificaciones.entity.LogAplicacion;
import java.time.Instant;

public record LogResponse(Long id, String servicio, String nivel, String mensaje,
                          String idCorrelacion, Instant fecha) {
    public static LogResponse from(LogAplicacion l) {
        return new LogResponse(l.getId(), l.getServicio(), l.getNivel(), l.getMensaje(),
            l.getCorrelationId(), l.getFecha());
    }
}
