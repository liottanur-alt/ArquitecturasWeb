package com.universidad.msvsectoerasiento.controller;

import com.universidad.msvsectoerasiento.dto.*;
import com.universidad.msvsectoerasiento.service.InventarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/eventos/{idEvento}") @RequiredArgsConstructor
public class InventarioController {
    private final InventarioService inventario;

    /** Inicializa las reglas de inventario del evento consultando al proveedor Eventos. */
    @PutMapping("/inventario")
    public ResponseEntity<Void> configurar(@PathVariable @Positive Long idEvento, @Valid @RequestBody InventarioRequest request) {
        inventario.configurarEvento(idEvento, request); return ResponseEntity.noContent().build();
    }

    /** Configura precio/capacidad del sector; repetir el mismo contenido es seguro. */
    @PutMapping("/sectores")
    public DisponibilidadResponse sector(@PathVariable @Positive Long idEvento, @Valid @RequestBody SectorEventoRequest request) {
        return inventario.configurarSector(idEvento, request);
    }

    /** Lista disponibilidad actual sin permitir almacenamiento en caches HTTP. */
    @GetMapping("/sectores")
    public ResponseEntity<List<DisponibilidadResponse>> listar(@PathVariable @Positive Long idEvento) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(inventario.consultar(idEvento));
    }

    /** Muestra la disponibilidad actual de un sector del evento. */
    @GetMapping("/sectores/{idSector}/disponibilidad")
    public ResponseEntity<DisponibilidadResponse> disponibilidad(@PathVariable @Positive Long idEvento, @PathVariable @Positive Long idSector) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(inventario.consultarSector(idEvento, idSector));
    }

    /** Compensa cancelaciones: libera inventario; el servicio Compras administra los reintegros. */
    @PostMapping("/inventario/cancelacion")
    public ResponseEntity<Void> cancelar(@PathVariable @Positive Long idEvento) {
        inventario.cancelar(idEvento); return ResponseEntity.noContent().build();
    }
}
