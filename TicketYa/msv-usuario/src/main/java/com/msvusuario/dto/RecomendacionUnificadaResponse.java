package com.msvusuario.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RecomendacionUnificadaResponse(Long idEvento, String nombre, String tipo, LocalDateTime fechaHora,
        String ciudad, int puntuacion, int puntuacionPreferencias, long compradoresEnComun,
        List<String> motivos, List<FuenteRecomendacion> fuentes) {
    public RecomendacionUnificadaResponse {
        motivos = List.copyOf(motivos);
        fuentes = List.copyOf(fuentes);
    }
}
