package msvevento.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notificaciones-evento", url = "${MSVC_NOTIFICACIONES_URL:http://localhost:8006}")
public interface NotificacionEventoClient {
    @PostMapping("/api/sucesos")
    void registrar(@RequestBody Suceso suceso);

    record Suceso(String claveIdempotencia, String tipo, Long idEvento, Long idCliente,
                  String destinatario, String detalle) {}
}
