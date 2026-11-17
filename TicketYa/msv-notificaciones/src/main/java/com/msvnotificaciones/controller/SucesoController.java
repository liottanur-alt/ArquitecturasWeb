package com.msvnotificaciones.controller;

import com.msvnotificaciones.dto.SucesoRequest;
import com.msvnotificaciones.dto.SucesoResponse;
import com.msvnotificaciones.service.SucesoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/sucesos")
public class SucesoController {
    private final SucesoService sucesos;
    public SucesoController(SucesoService sucesos) { this.sucesos = sucesos; }

    @PostMapping
    public ResponseEntity<SucesoResponse> registrar(@Valid @RequestBody SucesoRequest request) {
        var resultado = sucesos.registrar(request);
        return ResponseEntity.created(URI.create("/api/sucesos/" + resultado.getId()))
            .body(SucesoResponse.from(resultado));
    }
}
