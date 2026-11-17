package com.msvnotificaciones.repository;

import com.msvnotificaciones.entity.SucesoNegocio;
import com.msvnotificaciones.entity.TipoSuceso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface SucesoNegocioRepository extends JpaRepository<SucesoNegocio, Long> {
    Optional<SucesoNegocio> findByClaveIdempotencia(String claveIdempotencia);
    List<SucesoNegocio> findByIdEventoOrderByFechaSucesoDesc(Long idEvento);

    @Query("""
            SELECT SUM(s.cantidadEntradas)
            FROM SucesoNegocio s
            WHERE s.idEvento = :idEvento
            AND s.tipo = :tipo
            """)
    Long totalEntradas(@Param("idEvento") Long idEvento, @Param("tipo") TipoSuceso tipo);

    @Query("""
            SELECT SUM(s.importe)
            FROM SucesoNegocio s
            WHERE s.idEvento = :idEvento
            AND s.tipo = :tipo
            """)
    java.math.BigDecimal totalImporte(@Param("idEvento") Long idEvento, @Param("tipo") TipoSuceso tipo);

    @Query("""
            SELECT MAX(s.capacidadTotal)
            FROM SucesoNegocio s
            WHERE s.idEvento = :idEvento
            AND s.capacidadTotal IS NOT NULL
            """)
    Integer capacidad(@Param("idEvento") Long idEvento);
}
