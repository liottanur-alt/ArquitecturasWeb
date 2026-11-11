package org.msvcompraentradasol.feigns;

import org.msvcompraentradasol.dtos.EventoFechaResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "msvc-eventos", url = "${msvc.eventos.url}")
public interface EventoClientFeign {

    @GetMapping("/api/eventos/{id}")
    EventoFechaResponseDTO obtenerEvento(@PathVariable("id") Long idEvento);

    @PatchMapping("/api/eventos/{id}/cancelar")
    void cancelarEvento(@PathVariable("id") Long idEvento);
}
