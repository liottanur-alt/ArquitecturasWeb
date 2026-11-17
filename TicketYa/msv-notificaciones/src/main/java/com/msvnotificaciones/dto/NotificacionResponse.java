package com.msvnotificaciones.dto;

import com.msvnotificaciones.entity.Notificacion;
import java.time.Instant;

public record NotificacionResponse(Long id, Long idCliente, String asunto, String mensaje,
                                   String estado, Instant fechaCreacion) {
    public static NotificacionResponse from(Notificacion n) {
        return new NotificacionResponse(n.getId(), n.getIdCliente(), n.getAsunto(),
            n.getMensaje(), n.getEstado().name(), n.getFechaCreacion());
    }
}
