package com.msvnotificaciones.controller;

import com.msvnotificaciones.dto.ReporteEventoResponse;
import com.msvnotificaciones.service.ReporteService;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reportes/eventos")
public class ReporteController {
    private final ReporteService reportes;
    public ReporteController(ReporteService reportes) { this.reportes = reportes; }
    @GetMapping("/{idEvento}")
    public ReporteEventoResponse consultar(@PathVariable @Positive Long idEvento) {
        return reportes.porEvento(idEvento);
    }
}
