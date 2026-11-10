package org.msvcompraentradasol.repositories;

import org.msvcompraentradasol.entities.Compra;
import org.msvcompraentradasol.enums.EstadoCompra;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    @EntityGraph(attributePaths = "idsAsientos")
    Optional<Compra> findByClaveIdempotencia(String claveIdempotencia);

    @EntityGraph(attributePaths = "idsAsientos")
    List<Compra> findAllByIdClienteOrderByIdDesc(Long idCliente);

    @EntityGraph(attributePaths = "idsAsientos")
    @Query("select c from Compra c where c.id = :idCompra")
    Optional<Compra> buscarConAsientos(@Param("idCompra") Long idCompra);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compra c where c.id = :idCompra")
    Optional<Compra> buscarPorIdParaActualizar(@Param("idCompra") Long idCompra);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compra c where c.idEvento = :idEvento and c.estadoCompra in :estados order by c.id")
    List<Compra> buscarPorEventoParaActualizar(
            @Param("idEvento") Long idEvento,
            @Param("estados") List<EstadoCompra> estados
    );
}
