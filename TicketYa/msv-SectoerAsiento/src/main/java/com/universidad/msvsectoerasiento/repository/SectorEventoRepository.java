package com.universidad.msvsectoerasiento.repository;

import com.universidad.msvsectoerasiento.Entities.SectorEvento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SectorEventoRepository extends JpaRepository<SectorEvento, Long> {
    java.util.List<SectorEvento> findByIdEvento(Long evento);
    java.util.Optional<SectorEvento> findByIdEventoAndSectorId(Long evento, Long sector);
    boolean existsBySectorId(Long sectorId);
}
