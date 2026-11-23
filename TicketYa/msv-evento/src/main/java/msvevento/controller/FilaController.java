package msvevento.controller;

import java.security.Principal;
import msvevento.dto.TurnoFilaResponse;
import msvevento.dto.ValidarTurnoRequest;
import msvevento.dto.ValidarTurnoResponse;
import msvevento.service.FilaService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class FilaController {

    private final FilaService filaService;

    public FilaController(FilaService filaService) {
        this.filaService = filaService;
    }

    @PostMapping("/api/eventos/{eventoId}/fila")
    public TurnoFilaResponse entrar(@PathVariable Long eventoId, Principal principal) {
        return filaService.entrar(eventoId, clienteId(principal));
    }

    @GetMapping("/api/eventos/{eventoId}/fila/mi-turno")
    public TurnoFilaResponse consultar(@PathVariable Long eventoId, Principal principal) {
        return filaService.consultar(eventoId, clienteId(principal));
    }

    @PostMapping("/interno/fila/autorizaciones/validar")
    public ValidarTurnoResponse validar(@RequestBody ValidarTurnoRequest request) {
        return filaService.validarAutorizacion(
                request.eventoId(), clienteInterno(), request.autorizacionCompra(), request.idOperacion());
    }

    @PostMapping("/interno/fila/autorizaciones/completar")
    public ValidarTurnoResponse completar(@RequestBody ValidarTurnoRequest request) {
        return filaService.completarCompra(
                request.eventoId(), clienteInterno(), request.autorizacionCompra(), request.idOperacion());
    }

    private Long clienteInterno() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta operación solo está disponible para servicios autorizados.");
        }
        Object claim = jwt.getToken().getClaims().get("usuarioId");
        if (claim == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No pudimos identificar al cliente del turno.");
        }
        try {
            return claim instanceof Number numero ? numero.longValue() : Long.valueOf(claim.toString());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No pudimos identificar al cliente del turno.");
        }
    }

    private Long clienteId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere autenticación para usar la fila");
        }
        try {
            return Long.parseLong(principal.getName());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Volvé a iniciar sesión para usar la fila.");
        }
    }
}
