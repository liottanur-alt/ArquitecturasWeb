package org.msvcompraentradasol.controllers;

import org.msvcompraentradasol.repositories.CompraRepository;
import org.msvcompraentradasol.repositories.EntradaRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/interno/notificaciones/eventos")
public class DestinatarioCompraInternoController {
    private final CompraRepository compras;
    private final EntradaRepository entradas;

    public DestinatarioCompraInternoController(CompraRepository compras, EntradaRepository entradas) {
        this.compras = compras;
        this.entradas = entradas;
    }

    @GetMapping("/{id}/compradores")
    public List<Long> compradores(@PathVariable Long id, @RequestParam boolean cancelacion) {
        return compras.compradoresParaAviso(id, cancelacion);
    }

    @GetMapping("/{id}/ventas-por-sector")
    public List<EntradaRepository.VentasSector> ventasPorSector(@PathVariable Long id) {
        return entradas.ventasPorSector(id);
    }
}
