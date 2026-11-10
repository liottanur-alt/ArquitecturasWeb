package org.msvcompraentradasol.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.msvcompraentradasol.enums.EstadoSolicitud;

public class SolicitudResolucionDTO {

    @NotNull
    @Positive
    private Long idAdministrador;

    @NotNull
    private EstadoSolicitud estadoSolicitud;

    public Long getIdAdministrador() {
        return idAdministrador;
    }

    public void setIdAdministrador(Long idAdministrador) {
        this.idAdministrador = idAdministrador;
    }

    public EstadoSolicitud getEstadoSolicitud() {
        return estadoSolicitud;
    }

    public void setEstadoSolicitud(EstadoSolicitud estadoSolicitud) {
        this.estadoSolicitud = estadoSolicitud;
    }
}
