package com.msvusuario.dto;

import java.util.List;

public record RecomendacionesResponse(List<RecomendacionUnificadaResponse> recomendaciones,
        boolean parcial, List<FuenteRecomendacion> fuentesNoDisponibles, boolean historialVerificado,
        boolean disponibilidadVerificada) {
    public RecomendacionesResponse {
        recomendaciones = List.copyOf(recomendaciones);
        fuentesNoDisponibles = List.copyOf(fuentesNoDisponibles);
    }
}
