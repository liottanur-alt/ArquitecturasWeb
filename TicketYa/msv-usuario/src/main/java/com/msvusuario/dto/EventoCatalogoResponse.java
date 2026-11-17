package com.msvusuario.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.List;

// Contrato HTTP minimo de Eventos; sus entidades y enums no se comparten.
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoCatalogoResponse(Long id, String nombre, String tipo, LocalDateTime fechaHora,
                                    Lugar lugar, List<Participante> participantes,
                                    String estado, String estadoPublicacion) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Lugar(String ciudad) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Participante(Long id) {}
}
