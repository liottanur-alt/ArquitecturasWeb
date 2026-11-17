package com.msvusuario.dto;

import com.msvusuario.entity.TipoEvento;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// PUT reemplaza ambas preferencias: null o un campo omitido se normaliza a vacio.
public record PreferenciasRequest(List<TipoEvento> tiposEventoPreferidos, String ciudadPreferida) {
    @JsonCreator
    public static PreferenciasRequest desdeJson(
            @JsonProperty("tiposEventoPreferidos") List<TipoEvento> tipos,
            @JsonProperty("ciudadPreferida") Object ciudad) {
        // Evita que el deserializador convierta numeros o booleanos en una ciudad.
        if (ciudad != null && !(ciudad instanceof String)) {
            throw new IllegalArgumentException("ciudadPreferida debe ser texto o null");
        }
        return new PreferenciasRequest(tipos, (String) ciudad);
    }
}
