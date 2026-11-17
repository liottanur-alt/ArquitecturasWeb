package com.msvnotificaciones.controller;

import com.msvnotificaciones.dto.SucesoResponse;
import com.msvnotificaciones.repository.SucesoNegocioRepository;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/bitacora")
public class BitacoraController {
    private final SucesoNegocioRepository sucesos;
    public BitacoraController(SucesoNegocioRepository sucesos) { this.sucesos = sucesos; }
    @GetMapping
    public List<SucesoResponse> porEvento(@RequestParam @Positive Long idEvento) {
        return sucesos.findByIdEventoOrderByFechaSucesoDesc(idEvento).stream().map(SucesoResponse::from).toList();
    }
}
