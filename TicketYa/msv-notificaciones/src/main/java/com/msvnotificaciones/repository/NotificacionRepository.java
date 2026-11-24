package com.msvnotificaciones.repository;

import com.msvnotificaciones.entity.Notificacion;
import com.msvnotificaciones.entity.EstadoNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findTop100ByIdClienteOrderByFechaCreacionDescIdDesc(Long idCliente);
    boolean existsBySucesoId(Long sucesoId);
    boolean existsByIdAndIdCliente(Long id, Long idCliente);
    long countByIdClienteAndFechaLecturaIsNull(Long idCliente);

    @Modifying
    @Transactional
    @Query("update Notificacion n set n.estado = :estado where n.id = :id")
    void actualizarEnvio(@Param("id") Long id, @Param("estado") EstadoNotificacion estado);

    @Modifying
    @Query("update Notificacion n set n.fechaLectura = :fecha where n.idCliente = :cliente and n.id = :id and n.fechaLectura is null")
    void leer(@Param("cliente") Long cliente, @Param("id") Long id, @Param("fecha") Instant fecha);

    @Modifying
    @Query("update Notificacion n set n.fechaLectura = :fecha where n.idCliente = :cliente and n.fechaLectura is null")
    void leerTodas(@Param("cliente") Long cliente, @Param("fecha") Instant fecha);
}
