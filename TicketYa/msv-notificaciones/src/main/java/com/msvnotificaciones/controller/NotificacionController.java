package com.msvnotificaciones.controller;

import com.msvnotificaciones.dto.NotificacionResponse;
import com.msvnotificaciones.repository.NotificacionRepository;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {
    private final NotificacionRepository notificaciones;
    public NotificacionController(NotificacionRepository notificaciones) { this.notificaciones = notificaciones; }
    @GetMapping
    public Bandeja bandeja(@AuthenticationPrincipal Jwt jwt) {
        Long cliente = titular(jwt);
        return new Bandeja(listar(cliente), notificaciones.countByIdClienteAndFechaLecturaIsNull(cliente));
    }

    @GetMapping("/clientes/{idCliente}")
    public List<NotificacionResponse> porCliente(@PathVariable @Positive Long idCliente, @AuthenticationPrincipal Jwt jwt) {
        if (!titular(jwt).equals(idCliente)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La bandeja pertenece a otra cuenta");
        return listar(idCliente);
    }

    @PatchMapping("/{id}/lectura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void leer(@PathVariable @Positive Long id, @AuthenticationPrincipal Jwt jwt) {
        Long cliente = titular(jwt);
        if (!notificaciones.existsByIdAndIdCliente(id, cliente)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada en tu bandeja");
        }
        notificaciones.leer(cliente, id, Instant.now());
    }

    @PatchMapping("/lectura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void leerTodas(@AuthenticationPrincipal Jwt jwt) {
        notificaciones.leerTodas(titular(jwt), Instant.now());
    }

    private List<NotificacionResponse> listar(Long idCliente) {
        return notificaciones.findTop100ByIdClienteOrderByFechaCreacionDescIdDesc(idCliente)
            .stream().map(NotificacionResponse::from).toList();
    }

    private Long titular(Jwt jwt) {
        try {
            long id = Long.parseLong(jwt.getSubject());
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {}
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El token no identifica una cuenta de usuario");
    }

    public record Bandeja(List<NotificacionResponse> notificaciones, long noLeidas) {}
}
