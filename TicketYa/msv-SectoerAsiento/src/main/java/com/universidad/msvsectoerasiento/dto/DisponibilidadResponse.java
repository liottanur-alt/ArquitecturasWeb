package com.universidad.msvsectoerasiento.dto;
import com.universidad.msvsectoerasiento.enums.*;
import java.math.BigDecimal;
import java.util.List;
public record DisponibilidadResponse(Long idEvento, Long idSector, TipoSector tipo,
    BigDecimal precio, int capacidad, int disponibles, int reservados, int vendidos,
    boolean cancelado, List<Posicion> asientos) {
    public record Posicion(Long idAsiento, String fila, String columna, EstadoAsiento estado) {}
}
