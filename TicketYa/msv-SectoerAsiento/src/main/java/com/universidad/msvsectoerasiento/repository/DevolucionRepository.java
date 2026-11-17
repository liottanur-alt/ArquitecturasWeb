package com.universidad.msvsectoerasiento.repository;
import com.universidad.msvsectoerasiento.Entities.Devolucion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface DevolucionRepository extends JpaRepository<Devolucion, Long> {
    Optional<Devolucion> findByClave(String clave);
}
