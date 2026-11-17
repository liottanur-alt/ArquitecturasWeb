package org.msvcompraentradasol.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoFechaResponseDTO(
        Long id,
        LocalDateTime fechaHora,
        String estado,
        String estadoPublicacion,
        LugarDTO lugar
) {
    public Long idLugar() {
        return lugar == null ? null : lugar.id();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LugarDTO(Long id) {
    }
}
