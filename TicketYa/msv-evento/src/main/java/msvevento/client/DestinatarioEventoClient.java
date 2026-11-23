package msvevento.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@FeignClient(name = "destinatarios-evento", url = "${MSVC_USUARIOS_URL:http://localhost:8004}")
public interface DestinatarioEventoClient {
    @GetMapping("/interno/notificaciones/destinatarios/{id}")
    Destinatario porId(@PathVariable("id") Long id);

    @PostMapping("/interno/notificaciones/destinatarios/favoritos")
    List<Destinatario> porFavoritos(@RequestBody Favoritos favoritos);

    record Favoritos(String ciudad, List<Long> artistas) {}
    record Destinatario(Long id, String email) {}
}
