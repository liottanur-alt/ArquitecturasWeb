package com.msvnotificaciones.service;

import com.msvnotificaciones.entity.LogAplicacion;
import com.msvnotificaciones.dto.LogRequest;
import com.msvnotificaciones.repository.LogAplicacionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

@Service
@Validated
@Transactional(readOnly = true)
public class LogAplicacionService {
    private static final List<String> NIVELES = List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");
    private final LogAplicacionRepository logs;
    public LogAplicacionService(LogAplicacionRepository logs) { this.logs = logs; }

    @Transactional
    public LogAplicacion registrar(@NotNull @Valid LogRequest r) {
        if (!NIVELES.contains(r.nivel().toUpperCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "nivel debe ser TRACE, DEBUG, INFO, WARN o ERROR");
        }
        return logs.save(new LogAplicacion(r.servicio().trim(), r.nivel().toUpperCase(),
            r.mensaje().trim(), r.idCorrelacion(), r.fecha() == null ? Instant.now() : r.fecha()));
    }

    public List<LogAplicacion> consultar(String servicio, Instant desde, Instant hasta) {
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "desde debe ser anterior o igual a hasta");
        }
        if (servicio == null || servicio.isBlank()) return logs.findTop500ByFechaBetweenOrderByFechaDesc(desde, hasta);
        return logs.findTop500ByServicioAndFechaBetweenOrderByFechaDesc(servicio, desde, hasta);
    }
}
