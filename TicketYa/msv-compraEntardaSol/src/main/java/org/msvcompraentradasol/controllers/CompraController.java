package org.msvcompraentradasol.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.msvcompraentradasol.entities.Compra;
import org.msvcompraentradasol.dtos.CompraRequestDTO;
import org.msvcompraentradasol.services.CompraService;

@RestController
@RequestMapping("/compras")
public class CompraController {

    @Autowired
    private CompraService compraService;

    @PostMapping
    public ResponseEntity<Compra> crearCompra(@Valid @RequestBody CompraRequestDTO dto) {
        Compra compra = compraService.crearCompra(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(compra);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Compra> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(compraService.buscarPorId(id));
    }

    @PutMapping("/{id}/pago")
    public ResponseEntity<Compra> procesarPago(
            @PathVariable Long id,
            @RequestParam boolean aprobado) {

        Compra compra = compraService.procesarPago(id, aprobado);
        return ResponseEntity.ok(compra);
    }

    @PostMapping("/eventos/{idEvento}/cancelacion")
    public ResponseEntity<Integer> cancelarEvento(@PathVariable Long idEvento) {
        return ResponseEntity.ok(compraService.cancelarEvento(idEvento));
    }
}
