package org.msvcompraentradasol.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.msvcompraentradasol.dtos.EntradaResponseDTO;
import org.msvcompraentradasol.entities.Compra;
import org.msvcompraentradasol.entities.Entrada;
import org.msvcompraentradasol.enums.EstadoEntrada;
import org.msvcompraentradasol.enums.Estadoqr;
import org.msvcompraentradasol.repositories.EntradaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EntradaService {

    private final EntradaRepository entradaRepository;

    public EntradaService(EntradaRepository entradaRepository) {
        this.entradaRepository = entradaRepository;
    }

    public Entrada crearEntrada(
            Long idCliente,
            Long idEvento,
            Long idSector,
            Long idAsiento,
            BigDecimal precio,
            Compra compra
    ) {
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

    @Transactional(readOnly = true)
    public EntradaResponseDTO buscarPorId(Long id) {
        return toResponse(obtener(id));
    }

    @Transactional(readOnly = true)
    public EntradaResponseDTO buscarPorQr(String qr) {
        return toResponse(obtenerPorQr(qr));
    }

    @Transactional(readOnly = true)
    public List<EntradaResponseDTO> listarPorCompra(Long idCompra) {
        return entradaRepository.findAllByCompra_Id(idCompra).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EntradaResponseDTO> listarPorCliente(Long idCliente) {
        return entradaRepository.findAllByIdCliente(idCliente).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EntradaResponseDTO marcarUsada(Long idEntrada) {
        return toResponse(marcar(obtener(idEntrada)));
    }

    @Transactional
    public EntradaResponseDTO marcarUsadaPorQr(String qr) {
        return toResponse(marcar(obtenerPorQr(qr)));
    }

    private Entrada marcar(Entrada entrada) {
        if (entrada.getEstadoEntrada() != EstadoEntrada.PAGA) {
            throw new IllegalStateException("La entrada no está vigente");
        }
        if (entrada.getEstadoqr() == Estadoqr.ESCANEADO) {
            throw new IllegalStateException("La entrada ya fue utilizada");
        }

        entrada.setEstadoqr(Estadoqr.ESCANEADO);
        return entradaRepository.save(entrada);
    }

    private Entrada obtener(Long id) {
        return entradaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrada no encontrada"));
    }

    private Entrada obtenerPorQr(String qr) {
        return entradaRepository.findByQr(qr)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrada no encontrada"));
    }

    private EntradaResponseDTO toResponse(Entrada entrada) {
        return new EntradaResponseDTO(
                entrada.getId(),
                entrada.getQr(),
                entrada.getEstadoqr().name(),
                entrada.getIdCliente(),
                entrada.getIdEvento(),
                entrada.getIdSector(),
                entrada.getIdAsiento(),
                entrada.getPrecio(),
                entrada.getEstadoEntrada().name()
        );
    }
}
