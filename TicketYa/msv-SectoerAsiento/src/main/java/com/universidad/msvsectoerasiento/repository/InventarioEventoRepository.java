package com.universidad.msvsectoerasiento.repository;
import com.universidad.msvsectoerasiento.Entities.InventarioEvento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface InventarioEventoRepository extends JpaRepository<InventarioEvento, Long> {
    /** El bloqueo vive en la base y se conserva hasta confirmar o revertir la transaccion. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from InventarioEvento e where e.id = :id")
    Optional<InventarioEvento> bloquear(@Param("id") Long id);
}
