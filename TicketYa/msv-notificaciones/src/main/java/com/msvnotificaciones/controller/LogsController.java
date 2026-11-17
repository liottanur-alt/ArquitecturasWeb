package com.msvnotificaciones.controller;

import com.msvnotificaciones.entity.LogAplicacion;
import com.msvnotificaciones.dto.LogRequest;
import com.msvnotificaciones.dto.LogResponse;
import com.msvnotificaciones.service.LogAplicacionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/logs")
public class LogsController {
    private final LogAplicacionService logs;
    public LogsController(LogAplicacionService logs) { this.logs = logs; }
    @PostMapping
    public ResponseEntity<LogResponse> registrar(@Valid @RequestBody LogRequest request) {
        LogAplicacion guardado = logs.registrar(request);
        return ResponseEntity.created(URI.create("/api/logs/" + guardado.getId()))
            .body(LogResponse.from(guardado));
    }
    @GetMapping
    public List<LogResponse> consultar(@RequestParam(required = false) String servicio,
                                       @RequestParam Instant desde, @RequestParam Instant hasta) {
        return logs.consultar(servicio, desde, hasta).stream().map(LogResponse::from).toList();
    }
}
