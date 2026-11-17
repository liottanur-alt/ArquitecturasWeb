package com.msvnotificaciones.repository;

import com.msvnotificaciones.entity.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findTop100ByIdClienteOrderByFechaCreacionDesc(Long idCliente);
    boolean existsBySucesoId(Long sucesoId);
}
