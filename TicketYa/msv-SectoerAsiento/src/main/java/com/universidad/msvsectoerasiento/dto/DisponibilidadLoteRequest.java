package com.universidad.msvsectoerasiento.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record DisponibilidadLoteRequest(@NotNull List<@NotNull @Positive Long> idsEventos) {}
