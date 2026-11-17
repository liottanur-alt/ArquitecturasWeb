package org.msvcompraentradasol.dtos;

import java.time.Instant;
import org.msvcompraentradasol.entities.Entrada;
import org.msvcompraentradasol.entities.Solicitud;

public record SolicitudResponseDTO(
        Long id,
        Long idEntrada,
        Long idCompra,
        Long idCliente,
        Long idAdministrador,
        Instant fechaSolicitud,
        Instant fechaResolucion,
        String referenciaReintegro,
        String estadoReintegro,
        String descripcion,
        String estadoSolicitud
) {
    public static SolicitudResponseDTO from(Solicitud solicitud) {
        Entrada entrada = solicitud.getEntrada();
        return new SolicitudResponseDTO(
                solicitud.getId(),
                entrada == null ? null : entrada.getId(),
                entrada == null || entrada.getCompra() == null ? null : entrada.getCompra().getId(),
                solicitud.getIdCliente(),
                solicitud.getIdAdministrador(),
                solicitud.getFechaSolicitud(),
                solicitud.getFechaResolucion(),
                solicitud.getReferenciaReintegro(),
                solicitud.getEstadoReintegro() == null ? null : solicitud.getEstadoReintegro().name(),
                solicitud.getDescripcion(),
                solicitud.getEstadoSolicitud() == null ? null : solicitud.getEstadoSolicitud().name()
        );
    }
}
