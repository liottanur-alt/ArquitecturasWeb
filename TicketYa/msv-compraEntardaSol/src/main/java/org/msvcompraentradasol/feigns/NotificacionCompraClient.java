package org.msvcompraentradasol.feigns;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

@FeignClient(name = "notificaciones-compra", url = "${MSVC_NOTIFICACIONES_URL:http://localhost:8006}")
public interface NotificacionCompraClient {
    @PostMapping("/api/sucesos")
    void registrar(@RequestBody Suceso suceso);

    record Suceso(String claveIdempotencia, String tipo, Long idEvento, Long idCompra, Long idCliente,
                  String destinatario, Long cantidadEntradas, BigDecimal importe, String detalle) {}
}
