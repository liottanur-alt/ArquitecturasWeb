package com.msvusuario.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RecomendacionResponse(Long idEvento, String nombre, String tipo, LocalDateTime fechaHora,
                                   String ciudad, int puntuacion, List<String> motivos) {
    public RecomendacionResponse {
        motivos = List.copyOf(motivos);
    }
}
