package org.msvcompraentradasol.repositories;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.msvcompraentradasol.entities.Solicitud;
import org.msvcompraentradasol.enums.EstadoSolicitud;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Solicitud s where s.id = :id")
    Optional<Solicitud> findByIdParaResolver(@Param("id") Long id);

    boolean existsByEntrada_IdAndEstadoSolicitud(
            Long idEntrada,
            EstadoSolicitud estadoSolicitud
    );

    List<Solicitud> findAllByEntrada_IdOrderByIdAsc(Long idEntrada);

    List<Solicitud> findAllByEntrada_IdAndEstadoSolicitud(Long idEntrada, EstadoSolicitud estadoSolicitud);

    List<Solicitud> findAllByEstadoSolicitudOrderByIdAsc(EstadoSolicitud estadoSolicitud);

    List<Solicitud> findAllByIdClienteOrderByIdAsc(Long idCliente);
}
