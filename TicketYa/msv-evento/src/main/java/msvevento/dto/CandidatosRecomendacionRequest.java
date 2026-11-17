package msvevento.dto;

import jakarta.validation.constraints.*;
import msvevento.entity.TipoEvento;
import java.time.LocalDateTime;
import java.util.List;

public record CandidatosRecomendacionRequest(@NotNull List<@NotNull TipoEvento> tiposEventoPreferidos,
        @NotNull List<@NotNull @Positive Long> artistasFavoritos, @Size(max = 100) String ciudadPreferida,
        @NotNull LocalDateTime fechaDesde) {}
