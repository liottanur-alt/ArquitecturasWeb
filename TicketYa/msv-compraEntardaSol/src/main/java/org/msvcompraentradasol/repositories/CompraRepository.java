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
    @Query(value = """
            SELECT DISTINCT c.id_cliente FROM compra c JOIN entrada e ON e.id_compra = c.id
            WHERE c.id_evento = :idEvento AND (
                (c.estado_compra = 'APROBADA'
                 AND e.estado_entrada IN ('PAGA', 'SOLICITUD_DEVOLUCION', 'DEVOLUCION_RECHAZADA'))
                OR (:cancelacion = TRUE AND c.estado_compra = 'CANCELADA' AND e.estado_entrada = 'EVENTO_CANCELADO')
            )
            """, nativeQuery = true)
    List<Long> compradoresParaAviso(@Param("idEvento") Long idEvento, @Param("cancelacion") boolean cancelacion);

    @Query(value = """
            SELECT DISTINCT c.id_evento
            FROM compra c
            JOIN entrada e ON e.id_compra = c.id
            WHERE c.id_cliente = :idCliente
              AND c.estado_compra = 'APROBADA'
              AND e.estado_entrada IN ('PAGA', 'SOLICITUD_DEVOLUCION', 'DEVOLUCION_RECHAZADA')
            ORDER BY c.id_evento
            """, nativeQuery = true)
    List<Long> historialValido(@Param("idCliente") Long idCliente);

    // Cada relacion cliente-evento aparece una vez si conserva alguna entrada vigente.
    // COUNT DISTINCT evita sumar al mismo vecino por entradas, compras o cruces repetidos.
    @Query(value = """
            WITH historial_valido AS (
                SELECT DISTINCT c.id_cliente AS cliente, c.id_evento AS evento
                FROM compra c
                JOIN entrada e ON e.id_compra = c.id
                WHERE c.estado_compra = 'APROBADA'
                  AND e.estado_entrada IN ('PAGA', 'SOLICITUD_DEVOLUCION', 'DEVOLUCION_RECHAZADA')
            )
            SELECT candidatos.evento AS idEvento,
                   COUNT(DISTINCT candidatos.cliente) AS compradoresEnComun,
                   MIN(comunes.evento) AS idEventoReferencia
            FROM historial_valido candidatos
            JOIN historial_valido comunes ON comunes.cliente = candidatos.cliente
            JOIN historial_valido propios ON propios.evento = comunes.evento AND propios.cliente = :idCliente
            WHERE candidatos.cliente <> :idCliente
              AND NOT EXISTS (
                  SELECT 1 FROM historial_valido ya
                  WHERE ya.cliente = :idCliente AND ya.evento = candidatos.evento
              )
            GROUP BY candidatos.evento
            ORDER BY compradoresEnComun DESC, candidatos.evento ASC
            """, nativeQuery = true)
    List<CompraEnComunProjection> recomendarPorComprasEnComun(@Param("idCliente") Long idCliente);

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
