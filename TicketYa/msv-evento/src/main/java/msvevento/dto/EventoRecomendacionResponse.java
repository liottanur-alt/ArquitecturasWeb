package msvevento.dto;

import msvevento.entity.Evento;
import msvevento.entity.TipoEvento;
import msvevento.entity.EstadoEvento;
import msvevento.entity.EstadoPublicacion;
import java.time.LocalDateTime;
import java.util.List;

public record EventoRecomendacionResponse(Long id, String nombre, TipoEvento tipo, LocalDateTime fechaHora,
        Lugar lugar, List<Participante> participantes, EstadoEvento estado, EstadoPublicacion estadoPublicacion) {
    public record Lugar(String ciudad) {}
    public record Participante(Long id) {}
    public static EventoRecomendacionResponse de(Evento e) {
        return new EventoRecomendacionResponse(e.getId(), e.getNombre(), e.getTipo(), e.getFechaHora(),
                e.getLugar() == null ? null : new Lugar(e.getLugar().getCiudad()),
                e.getParticipantes().stream().map(a -> new Participante(a.getId())).toList(), e.getEstado(), e.getEstadoPublicacion());
    }
}
