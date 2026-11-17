package com.msvusuario.service;

import com.msvusuario.dto.EventoCatalogoResponse;
import java.time.LocalDateTime;

// Misma regla de venta para recomendaciones por preferencias y por compras.
final class EventosRecomendables {
    private EventosRecomendables() {}

    static boolean disponible(EventoCatalogoResponse evento, LocalDateTime ahora) {
        return evento != null && evento.id() != null && evento.id() > 0
                && evento.nombre() != null && !evento.nombre().isBlank()
                && evento.tipo() != null && !evento.tipo().isBlank()
                && evento.fechaHora() != null && evento.fechaHora().isAfter(ahora)
                && "PUBLICADO".equals(evento.estadoPublicacion())
                && "EN_CURSO".equals(evento.estado());
    }
}
