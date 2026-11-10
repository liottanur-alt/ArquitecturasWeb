package org.msvcompraentradasol.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoFechaResponseDTO(Long id, LocalDateTime fechaHora) {
}
