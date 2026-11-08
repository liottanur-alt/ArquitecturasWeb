package com.universidad.msvsectoerasiento.client.dto;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Proyeccion del contrato real de Eventos, que devuelve el lugar anidado. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventoResponse(Long id, String nombre, LugarResponse lugar, Long idOrganizador,
                             LocalDateTime fechaHora, String estado, String estadoPublicacion) {
    /** Expone el ID externo sin incorporar entidades JPA del otro microservicio. */
    public Long idLugar() { return lugar == null ? null : lugar.id(); }
}
