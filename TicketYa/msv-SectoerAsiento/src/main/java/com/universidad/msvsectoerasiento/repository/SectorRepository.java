package com.universidad.msvsectoerasiento.repository;

import com.universidad.msvsectoerasiento.Entities.Sector;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SectorRepository extends JpaRepository<Sector, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sector s where s.id = :id")
    Optional<Sector> buscarParaActualizar(@Param("id") Long id);

    List<Sector> findByIdLugar(Long idLugar);
}
