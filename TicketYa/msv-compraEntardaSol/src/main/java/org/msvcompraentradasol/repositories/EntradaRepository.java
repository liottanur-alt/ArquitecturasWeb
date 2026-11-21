package org.msvcompraentradasol.repositories;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.msvcompraentradasol.entities.Entrada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntradaRepository extends JpaRepository<Entrada, Long> {

    List<Entrada> findAllByCompra_Id(Long idCompra);

    List<Entrada> findAllByIdCliente(Long idCliente);

    Optional<Entrada> findByQr(String qr);

    /** El bloqueo vive en la base hasta confirmar: evita que dos escaneos concurrentes marquen la misma entrada. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Entrada e where e.qr = :qr")
    Optional<Entrada> findByQrParaActualizar(@Param("qr") String qr);

    boolean existsByCompra_Id(Long idCompra);
}
