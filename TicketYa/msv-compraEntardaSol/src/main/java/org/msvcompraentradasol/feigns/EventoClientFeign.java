package org.msvcompraentradasol.feigns;

import org.msvcompraentradasol.dtos.EventoFechaResponseDTO;
import org.msvcompraentradasol.dtos.ValidarAutorizacionFilaRequestDTO;
import org.msvcompraentradasol.dtos.ValidarAutorizacionFilaResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "msvc-eventos", url = "${msvc.eventos.url}")
public interface EventoClientFeign {

    @GetMapping("/api/eventos/interno/{id}")
    EventoFechaResponseDTO obtenerEvento(@PathVariable("id") Long idEvento);

    @PatchMapping("/api/eventos/{id}/cancelar")
    void cancelarEvento(@PathVariable("id") Long idEvento);

    @PostMapping("/interno/fila/autorizaciones/validar")
    ValidarAutorizacionFilaResponseDTO validarAutorizacionFila(
            @RequestBody ValidarAutorizacionFilaRequestDTO request);

    @PostMapping("/interno/fila/autorizaciones/completar")
    ValidarAutorizacionFilaResponseDTO completarCompraEnFila(
            @RequestBody ValidarAutorizacionFilaRequestDTO request);
}
