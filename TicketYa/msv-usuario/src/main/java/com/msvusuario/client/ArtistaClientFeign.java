package com.msvusuario.client;

import com.msvusuario.dto.ArtistaResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "msvc-artistas", url = "${msvc.artistas.url}")
public interface ArtistaClientFeign {

    @GetMapping("/artista/{id}")
    ArtistaResponse obtenerPorId(@PathVariable("id") Long id);
}
