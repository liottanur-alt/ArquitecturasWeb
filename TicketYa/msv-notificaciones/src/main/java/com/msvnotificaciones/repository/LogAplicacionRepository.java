package com.msvnotificaciones.repository;

import com.msvnotificaciones.entity.LogAplicacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface LogAplicacionRepository extends JpaRepository<LogAplicacion, Long> {
    List<LogAplicacion> findTop500ByServicioAndFechaBetweenOrderByFechaDesc(String servicio, Instant desde, Instant hasta);
    List<LogAplicacion> findTop500ByFechaBetweenOrderByFechaDesc(Instant desde, Instant hasta);
}
