package org.msvcompraentradasol.feigns;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "destinatarios-compra", url = "${MSVC_USUARIOS_URL:http://localhost:8004}")
public interface DestinatarioCompraClient {
    @GetMapping("/interno/notificaciones/destinatarios/{id}")
    Destinatario porId(@PathVariable("id") Long id);

    record Destinatario(String email) {}
}
