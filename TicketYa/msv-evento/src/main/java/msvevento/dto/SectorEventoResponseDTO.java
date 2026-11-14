package msvevento.dto;

import java.math.BigDecimal;

/** Datos de inventario necesarios para validar la publicacion del evento. */
public record SectorEventoResponseDTO(Long idEvento, Long idSector, BigDecimal precio, Integer capacidad) {
}
