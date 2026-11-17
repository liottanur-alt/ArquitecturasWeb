package com.msvnotificaciones.controller;

import com.msvnotificaciones.dto.NotificacionResponse;
import com.msvnotificaciones.repository.NotificacionRepository;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {
    private final NotificacionRepository notificaciones;
    public NotificacionController(NotificacionRepository notificaciones) { this.notificaciones = notificaciones; }
    @GetMapping("/clientes/{idCliente}")
    public List<NotificacionResponse> porCliente(@PathVariable @Positive Long idCliente) {
        return notificaciones.findTop100ByIdClienteOrderByFechaCreacionDesc(idCliente)
            .stream().map(NotificacionResponse::from).toList();
    }
}
