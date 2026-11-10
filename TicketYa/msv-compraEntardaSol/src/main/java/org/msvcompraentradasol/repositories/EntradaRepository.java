package org.msvcompraentradasol.repositories;

import java.util.List;
import java.util.Optional;
import org.msvcompraentradasol.entities.Entrada;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntradaRepository extends JpaRepository<Entrada, Long> {

    List<Entrada> findAllByCompra_Id(Long idCompra);

    List<Entrada> findAllByIdCliente(Long idCliente);

    Optional<Entrada> findByQr(String qr);

    boolean existsByCompra_Id(Long idCompra);
}
