package org.msvcompraentradasol.repositories;

import java.util.List;
import org.msvcompraentradasol.entities.Entrada;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntradaRepository extends JpaRepository<Entrada, Long> {

    List<Entrada> findAllByCompra_Id(Long idCompra);

    List<Entrada> findAllByIdCliente(Long idCliente);
}
