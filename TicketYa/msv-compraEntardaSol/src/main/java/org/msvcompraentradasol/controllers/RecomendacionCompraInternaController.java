package org.msvcompraentradasol.controllers;

import java.util.List;
import org.msvcompraentradasol.dtos.CompraEnComunResponseDTO;
import org.msvcompraentradasol.dtos.HistorialValidoResponseDTO;
import org.msvcompraentradasol.services.RecomendacionCompraService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// El prefijo /interno no se publica por las rutas del Gateway.
@RestController
@RequestMapping("/interno/recomendaciones")
public class RecomendacionCompraInternaController {
    private final RecomendacionCompraService recomendaciones;

    public RecomendacionCompraInternaController(RecomendacionCompraService recomendaciones) {
        this.recomendaciones = recomendaciones;
    }

    @GetMapping("/clientes/{idCliente}")
    public List<CompraEnComunResponseDTO> recomendar(@PathVariable Long idCliente) {
        return recomendaciones.recomendar(idCliente);
    }

    @GetMapping("/clientes/{idCliente}/historial-valido")
    public HistorialValidoResponseDTO historialValido(@PathVariable Long idCliente) {
        return recomendaciones.historialValido(idCliente);
    }
}
