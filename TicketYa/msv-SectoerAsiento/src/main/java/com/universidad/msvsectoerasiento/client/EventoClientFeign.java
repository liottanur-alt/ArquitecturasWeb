package com.universidad.msvsectoerasiento.client;

import com.universidad.msvsectoerasiento.client.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;


@FeignClient(name = "msvc-eventos", url = "${msvc.eventos.url}")
public interface EventoClientFeign {
    @GetMapping("/api/eventos/interno/{id}")
    EventoResponse obtenerEvento(@PathVariable("id") Long id);

    @PatchMapping("/api/eventos/interno/{id}/disponibilidad")
    void actualizarDisponibilidad(@PathVariable("id") Long id, @RequestParam("agotado") boolean agotado);

    @GetMapping("/api/lugares/{id}")
    LugarResponse obtenerLugar(@PathVariable("id") Long id);
}
