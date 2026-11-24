package com.universidad.msvsectoerasiento.repository;

import com.universidad.msvsectoerasiento.Entities.SectorEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SectorEventoRepository extends JpaRepository<SectorEvento, Long> {
    java.util.List<SectorEvento> findByIdEvento(Long evento);
    java.util.Optional<SectorEvento> findByIdEventoAndSectorId(Long evento, Long sector);
    boolean existsBySectorId(Long sectorId);

    @Query("select s.idEvento as idEvento, min(s.precio) as precio from SectorEvento s "
            + "where s.idEvento in :ids group by s.idEvento")
    List<PrecioEvento> preciosMinimos(@Param("ids") List<Long> ids);

    interface PrecioEvento {
        Long getIdEvento();
        BigDecimal getPrecio();
    }
}
