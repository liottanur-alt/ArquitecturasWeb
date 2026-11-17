package org.msvcompraentradasol.controllers;

import jakarta.validation.Valid;
import java.util.List;
import org.msvcompraentradasol.dtos.CancelacionEventoResponseDTO;
import org.msvcompraentradasol.dtos.CompraRequestDTO;
import org.msvcompraentradasol.dtos.CompraResponseDTO;
import org.msvcompraentradasol.services.CompraService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/compras")
public class CompraController {

    private final CompraService compraService;

    public CompraController(CompraService compraService) {
        this.compraService = compraService;
    }

    @PostMapping
    public ResponseEntity<CompraResponseDTO> crearCompra(@Valid @RequestBody CompraRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(compraService.crearCompra(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompraResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(compraService.buscarPorId(id));
    }

    @GetMapping("/cliente/{idCliente}")
    public ResponseEntity<List<CompraResponseDTO>> listarPorCliente(@PathVariable Long idCliente) {
        return ResponseEntity.ok(compraService.listarPorCliente(idCliente));
    }

    @PutMapping("/{id}/pago")
    public ResponseEntity<CompraResponseDTO> procesarPago(
            @PathVariable Long id,
            @RequestParam boolean aprobado
    ) {
        return ResponseEntity.ok(compraService.procesarPago(id, aprobado));
    }

    @PostMapping("/eventos/{idEvento}/cancelacion")
    public ResponseEntity<CancelacionEventoResponseDTO> cancelarEvento(@PathVariable Long idEvento) {
        return ResponseEntity.ok(compraService.cancelarEvento(idEvento));
    }
}
