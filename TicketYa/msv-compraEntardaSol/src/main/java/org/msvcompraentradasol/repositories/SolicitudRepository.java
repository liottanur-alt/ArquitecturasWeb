package org.msvcompraentradasol.repositories;

import java.util.List;
import org.msvcompraentradasol.entities.Solicitud;
import org.msvcompraentradasol.enums.EstadoSolicitud;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {

    boolean existsByEntrada_IdAndEstadoSolicitud(
            Long idEntrada,
            EstadoSolicitud estadoSolicitud
    );

    List<Solicitud> findAllByEntrada_IdOrderByIdAsc(Long idEntrada);

    List<Solicitud> findAllByEstadoSolicitudOrderByIdAsc(EstadoSolicitud estadoSolicitud);
}
