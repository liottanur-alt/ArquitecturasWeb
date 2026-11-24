package com.universidad.msvsectoerasiento.controller;

import com.universidad.msvsectoerasiento.dto.*;
import com.universidad.msvsectoerasiento.service.InventarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/reservas") @RequiredArgsConstructor
public class ReservaController {
    private final InventarioService inventario;

    /** Crea o recupera una reserva usando la clave de idempotencia del pedido. */
    @PostMapping
    public ResponseEntity<ReservaResponse> reservar(@Valid @RequestBody ReservaRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(inventario.reservar(conIdentidad(request)));
    }

    /** Consulta el estado actualizado de la reserva. */
    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponse> obtener(@PathVariable @Positive Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(inventario.obtenerReserva(id));
    }

    /** Compras invoca esta operacion tras aprobar el pago y debe comprobar el estado devuelto. */
    @PostMapping("/{id}/confirmacion")
    public ReservaResponse confirmar(@PathVariable @Positive Long id) { return inventario.confirmar(id); }

    /** Compensa pagos rechazados o compras abandonadas. */
    @PostMapping("/{id}/liberacion")
    public ReservaResponse liberar(@PathVariable @Positive Long id) { return inventario.liberar(id); }

    /** Aplica la liberacion de lugares de una devolucion aprobada. */
    @PostMapping("/{id}/devoluciones")
    public ReservaResponse devolver(@PathVariable @Positive Long id, @Valid @RequestBody DevolucionRequest request) {
        return inventario.devolver(id, request);
    }

    private ReservaRequest conIdentidad(ReservaRequest request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Identidad del cliente no disponible");
        }
        Object claim = jwt.getToken().getClaims().get("usuarioId");
        Long usuarioId = null;
        if (claim instanceof Number numero) {
            usuarioId = numero.longValue();
        } else if (claim instanceof String texto) {
            try {
                usuarioId = Long.valueOf(texto);
            } catch (NumberFormatException ignorado) {
            }
        }
        if (usuarioId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Identidad del cliente no disponible");
        }
        return new ReservaRequest(request.claveIdempotencia(), usuarioId, request.idEvento(),
                request.idSector(), request.cantidad(), request.idAsientos());
    }
}
