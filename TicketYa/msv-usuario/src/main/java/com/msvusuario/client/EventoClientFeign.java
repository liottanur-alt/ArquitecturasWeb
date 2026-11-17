package com.msvusuario.client;

import com.msvusuario.dto.EventoCatalogoResponse;
import com.msvusuario.dto.CandidatosRecomendacionRequest;
import com.msvusuario.dto.EventosPorIdsRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;

@FeignClient(name = "eventos-recomendaciones", url = "${msv.evento.url}")
public interface EventoClientFeign {
    @PostMapping("/interno/eventos/recomendaciones/candidatos")
    List<EventoCatalogoResponse> buscarCandidatos(@RequestBody CandidatosRecomendacionRequest request);

    @PostMapping("/interno/eventos/recomendaciones/por-ids")
    List<EventoCatalogoResponse> consultarPorIds(@RequestBody EventosPorIdsRequest request);
}
