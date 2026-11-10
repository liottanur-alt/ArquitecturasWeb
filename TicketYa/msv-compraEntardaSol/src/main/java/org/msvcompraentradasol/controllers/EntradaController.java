package org.msvcompraentradasol.controllers;

import java.math.BigDecimal;
import java.util.List;
import org.msvcompraentradasol.entities.Entrada;
import org.msvcompraentradasol.services.EntradaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/entradas")
public class EntradaController {

    private final EntradaService entradaService;

    public EntradaController(EntradaService entradaService) {
        this.entradaService = entradaService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntradaResponse> buscarPorId(@PathVariable Long id) {
        Entrada entrada = entradaService.buscarPorId(id);
        return ResponseEntity.ok(toResponse(entrada));
    }

    @GetMapping("/compra/{idCompra}")
    public ResponseEntity<List<EntradaResponse>> listarPorCompra(@PathVariable Long idCompra) {
        List<EntradaResponse> entradas = entradaService.listarPorCompra(idCompra)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(entradas);
    }

    @GetMapping("/cliente/{idCliente}")
    public ResponseEntity<List<EntradaResponse>> listarPorCliente(@PathVariable Long idCliente) {
        List<EntradaResponse> entradas = entradaService.listarPorCliente(idCliente)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(entradas);
    }

    @PatchMapping("/{id}/uso")
    public ResponseEntity<EntradaResponse> marcarUsada(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(entradaService.marcarUsada(id)));
    }

    private EntradaResponse toResponse(Entrada entrada) {
        return new EntradaResponse(
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

    /** Contrato de respuesta consumido por el cliente Feign de SectorAsiento. */
    public record EntradaResponse(
            Long id,
            String qr,
            String estadoQr,
            Long idCliente,
            Long idEvento,
            Long idSector,
            Long idAsiento,
            BigDecimal precio,
            String estadoEntrada
    ) {}
}
