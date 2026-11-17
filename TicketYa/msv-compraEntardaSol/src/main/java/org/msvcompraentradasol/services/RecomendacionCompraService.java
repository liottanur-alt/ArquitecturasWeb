package org.msvcompraentradasol.services;

import java.util.List;
import org.msvcompraentradasol.dtos.CompraEnComunResponseDTO;
import org.msvcompraentradasol.dtos.HistorialValidoResponseDTO;
import org.msvcompraentradasol.repositories.CompraRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class RecomendacionCompraService {
    private final CompraRepository compras;

    public RecomendacionCompraService(CompraRepository compras) { this.compras = compras; }

    public HistorialValidoResponseDTO historialValido(Long idCliente) {
        if (idCliente == null || idCliente <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCliente debe ser positivo");
        }
        return new HistorialValidoResponseDTO(compras.historialValido(idCliente));
    }

    public List<CompraEnComunResponseDTO> recomendar(Long idCliente) {
        if (idCliente == null || idCliente <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "idCliente debe ser positivo");
        }
        return compras.recomendarPorComprasEnComun(idCliente).stream()
                .map(fila -> new CompraEnComunResponseDTO(fila.getIdEvento(), fila.getCompradoresEnComun(),
                        fila.getIdEventoReferencia())).toList();
    }
}
