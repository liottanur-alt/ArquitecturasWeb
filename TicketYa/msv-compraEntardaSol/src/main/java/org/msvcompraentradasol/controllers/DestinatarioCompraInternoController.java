package org.msvcompraentradasol.controllers;

import org.msvcompraentradasol.repositories.CompraRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/interno/notificaciones/eventos")
public class DestinatarioCompraInternoController {
    private final CompraRepository compras;

    public DestinatarioCompraInternoController(CompraRepository compras) {
        this.compras = compras;
    }

    @GetMapping("/{id}/compradores")
    public List<Long> compradores(@PathVariable Long id, @RequestParam boolean cancelacion) {
        return compras.compradoresParaAviso(id, cancelacion);
    }
}
