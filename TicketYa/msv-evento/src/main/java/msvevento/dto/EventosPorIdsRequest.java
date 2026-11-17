package msvevento.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record EventosPorIdsRequest(@NotNull List<@NotNull @Positive Long> idsEventos) {}
