package org.msvcompraentradasol.dtos;

import java.math.BigDecimal;
import java.util.List;
import org.msvcompraentradasol.entities.Compra;

public record CompraResponseDTO(
        Long id,
        Long idCliente,
        Long idEvento,
        Long idSector,
        Long cantidadEntradas,
        String medioDePago,
        String descripcion,
        String estadoCompra,
        String claveIdempotencia,
        Long idReserva,
        BigDecimal precioUnitario,
        BigDecimal importeTotal,
        String referenciaPago,
        String referenciaReintegro,
        String estadoReintegro,
        List<Long> idsAsientos
) {
    public static CompraResponseDTO from(Compra compra) {
        return new CompraResponseDTO(
                compra.getId(),
                compra.getIdCliente(),
                compra.getIdEvento(),
                compra.getIdSector(),
                compra.getCantidadEntradas(),
                compra.getMedioDePago(),
                compra.getDescripcion(),
                compra.getEstadoCompra() == null ? null : compra.getEstadoCompra().name(),
                compra.getClaveIdempotencia(),
                compra.getIdReserva(),
                compra.getPrecioUnitario(),
                compra.getImporteTotal(),
                compra.getReferenciaPago(),
                compra.getReferenciaReintegro(),
                compra.getEstadoReintegro() == null ? null : compra.getEstadoReintegro().name(),
                compra.getIdsAsientos() == null ? List.of() : List.copyOf(compra.getIdsAsientos())
        );
    }
}
