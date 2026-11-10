package org.msvcompraentradasol.controllers;

import java.util.List;
import org.msvcompraentradasol.dtos.EntradaResponseDTO;
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
    public ResponseEntity<EntradaResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(entradaService.buscarPorId(id));
    }

    @GetMapping("/qr/{qr}")
    public ResponseEntity<EntradaResponseDTO> buscarPorQr(@PathVariable String qr) {
        return ResponseEntity.ok(entradaService.buscarPorQr(qr));
    }

    @GetMapping("/compra/{idCompra}")
    public ResponseEntity<List<EntradaResponseDTO>> listarPorCompra(@PathVariable Long idCompra) {
        return ResponseEntity.ok(entradaService.listarPorCompra(idCompra));
    }

    @GetMapping("/cliente/{idCliente}")
    public ResponseEntity<List<EntradaResponseDTO>> listarPorCliente(@PathVariable Long idCliente) {
        return ResponseEntity.ok(entradaService.listarPorCliente(idCliente));
    }

    @PatchMapping("/{id}/uso")
    public ResponseEntity<EntradaResponseDTO> marcarUsada(@PathVariable Long id) {
        return ResponseEntity.ok(entradaService.marcarUsada(id));
    }

    @PatchMapping("/qr/{qr}/uso")
    public ResponseEntity<EntradaResponseDTO> marcarUsadaPorQr(@PathVariable String qr) {
        return ResponseEntity.ok(entradaService.marcarUsadaPorQr(qr));
    }
}
