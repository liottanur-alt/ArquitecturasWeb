package com.msvusuario.client;

import com.msvusuario.dto.ArtistaResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "msv-evento", url = "${msv.evento.url}")
public interface ArtistaClientFeign {

    @GetMapping("/api/artistas/{id}")
    ArtistaResponse obtenerPorId(@PathVariable("id") Long id);
}