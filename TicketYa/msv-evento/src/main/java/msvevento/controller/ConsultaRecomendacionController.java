package msvevento.controller;

import jakarta.validation.Valid;
import msvevento.dto.CandidatosRecomendacionRequest;
import msvevento.dto.EventosPorIdsRequest;
import msvevento.dto.EventoRecomendacionResponse;
import msvevento.service.ConsultaRecomendacionService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/interno/eventos/recomendaciones")
public class ConsultaRecomendacionController {
    private final ConsultaRecomendacionService consultas;
    public ConsultaRecomendacionController(ConsultaRecomendacionService consultas) { this.consultas = consultas; }

    @PostMapping("/candidatos")
    public List<EventoRecomendacionResponse> candidatos(@Valid @RequestBody CandidatosRecomendacionRequest request) {
        return consultas.candidatos(request);
    }

    @PostMapping("/por-ids")
    public List<EventoRecomendacionResponse> porIds(@Valid @RequestBody EventosPorIdsRequest request) {
        return consultas.porIds(request);
    }
}
