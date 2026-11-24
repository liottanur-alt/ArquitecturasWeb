package org.msvcompraentradasol.repositories;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.msvcompraentradasol.entities.Entrada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntradaRepository extends JpaRepository<Entrada, Long> {
    @Query(value = """
            SELECT id_sector AS idSector, COUNT(*) AS vendidas,
                   SUM(CASE WHEN estado_entrada IN ('DEVOLUCION_ACEPTADA','EVENTO_CANCELADO') THEN 1 ELSE 0 END) AS devueltas,
                   SUM(CASE WHEN estado_entrada IN ('DEVOLUCION_ACEPTADA','EVENTO_CANCELADO') THEN 0 ELSE precio END) AS ingresosNetos
            FROM entrada WHERE id_evento = :evento GROUP BY id_sector
            """, nativeQuery = true)
    List<VentasSector> ventasPorSector(@Param("evento") Long idEvento);

    interface VentasSector {
        Long getIdSector();
        long getVendidas();
        long getDevueltas();
        BigDecimal getIngresosNetos();
    }

    List<Entrada> findAllByCompra_Id(Long idCompra);

    List<Entrada> findAllByIdCliente(Long idCliente);

    Optional<Entrada> findByQr(String qr);

    /** El bloqueo vive en la base hasta confirmar: evita que dos escaneos concurrentes marquen la misma entrada. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Entrada e where e.qr = :qr")
    Optional<Entrada> findByQrParaActualizar(@Param("qr") String qr);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Entrada e where e.id = :id")
    Optional<Entrada> findByIdParaActualizar(@Param("id") Long id);

    boolean existsByCompra_Id(Long idCompra);
}
