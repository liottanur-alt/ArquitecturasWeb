package com.msvusuario.dto;

import java.util.List;

// El controlador conserva el array HTTP e informa la verificacion por cabeceras.
public record ResultadoFuenteRecomendacion<T>(List<T> recomendaciones, boolean historialVerificado,
        boolean disponibilidadVerificada) {
    public ResultadoFuenteRecomendacion { recomendaciones = List.copyOf(recomendaciones); }
}
