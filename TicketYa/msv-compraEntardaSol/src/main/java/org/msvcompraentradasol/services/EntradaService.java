package org.msvcompraentradasol.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.msvcompraentradasol.entities.Compra;
import org.msvcompraentradasol.entities.Entrada;
import org.msvcompraentradasol.enums.EstadoEntrada;
import org.msvcompraentradasol.enums.Estadoqr;
import org.msvcompraentradasol.repositories.EntradaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;


@Service
public class EntradaService {

    @Autowired
    private EntradaRepository entradaRepository;

    public Entrada crearEntrada(Long idCliente, Long idEvento, Long idSector,
                                Long idAsiento, BigDecimal precio, Compra compra) {

        Entrada entrada = new Entrada();
        entrada.setQr(UUID.randomUUID().toString());
        entrada.setIdCliente(idCliente);
        entrada.setIdEvento(idEvento);
        entrada.setIdSector(idSector);
        entrada.setIdAsiento(idAsiento);
        entrada.setPrecio(precio);
        entrada.setCompra(compra);
        entrada.setEstadoEntrada(EstadoEntrada.PAGA);
        entrada.setEstadoqr(Estadoqr.NO_ESCANEADO);

        return entradaRepository.save(entrada);
    }

    public Entrada buscarPorId(Long id) {
        return entradaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrada no encontrada"));
    }

    public List<Entrada> listarPorCompra(Long idCompra) {
        return entradaRepository.findAllByCompra_Id(idCompra);
    }

    public List<Entrada> listarPorCliente(Long idCliente) {
        return entradaRepository.findAllByIdCliente(idCliente);
    }

    public Entrada marcarUsada(Long idEntrada) {
        Entrada entrada = buscarPorId(idEntrada);
        if (entrada.getEstadoEntrada() != EstadoEntrada.PAGA) {
            throw new IllegalStateException("La entrada no está vigente");
        }
        if (entrada.getEstadoqr() == Estadoqr.ESCANEADO) {
            throw new IllegalStateException("La entrada ya fue utilizada");
        }

        entrada.setEstadoqr(Estadoqr.ESCANEADO);
        return entradaRepository.save(entrada);
    }

}
