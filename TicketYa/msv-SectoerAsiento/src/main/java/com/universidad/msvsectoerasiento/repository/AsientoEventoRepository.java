package com.universidad.msvsectoerasiento.repository;

import com.universidad.msvsectoerasiento.Entities.AsientoEvento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AsientoEventoRepository extends JpaRepository<AsientoEvento, Long> {
    java.util.List<AsientoEvento> findBySectorEventoIdOrderByIdAsc(Long sectorEvento);
    boolean existsByAsientoId(Long asientoId);
}
