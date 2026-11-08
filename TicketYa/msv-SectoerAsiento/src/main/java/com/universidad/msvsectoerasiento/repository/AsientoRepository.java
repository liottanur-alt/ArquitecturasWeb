package com.universidad.msvsectoerasiento.repository;

import com.universidad.msvsectoerasiento.Entities.Asiento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsientoRepository extends JpaRepository<Asiento, Long> {
    /** Lee solo el ID para poder bloquear el sector antes de cargar el asiento editable. */
    @Query("select a.sector.id from Asiento a where a.id = :id")
    Optional<Long> buscarIdSector(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Asiento a where a.id = :id")
    Optional<Asiento> buscarParaActualizar(@Param("id") Long id);

    List<Asiento> findBySectorId(Long sectorId);
    long countBySectorId(Long sectorId);
    boolean existsBySectorIdAndFilaAndColumnaAndIdNot(Long sectorId, String fila, String columna, Long id);
    boolean existsBySectorIdAndFilaAndColumna(Long sectorId, String fila, String columna);
    boolean existsBySectorId(Long sectorId);
}
