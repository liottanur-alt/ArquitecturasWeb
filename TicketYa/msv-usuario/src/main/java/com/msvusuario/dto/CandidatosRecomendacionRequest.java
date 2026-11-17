package com.msvusuario.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CandidatosRecomendacionRequest(List<String> tiposEventoPreferidos, List<Long> artistasFavoritos,
        String ciudadPreferida, LocalDateTime fechaDesde) {}
