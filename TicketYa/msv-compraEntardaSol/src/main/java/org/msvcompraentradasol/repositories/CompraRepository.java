package org.msvcompraentradasol.repositories;

import org.msvcompraentradasol.entities.Compra;
import org.msvcompraentradasol.enums.EstadoCompra;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    Optional<Compra> findByClaveIdempotencia(String claveIdempotencia);

    List<Compra> findAllByIdEventoAndEstadoCompraIn(Long idEvento, List<EstadoCompra> estados);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compra c where c.id = :idCompra")
    Optional<Compra> buscarPorIdParaActualizar(@Param("idCompra") Long idCompra);
}
