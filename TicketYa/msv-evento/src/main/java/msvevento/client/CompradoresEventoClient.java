package msvevento.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.math.BigDecimal;
import java.util.List;

@FeignClient(name = "compradores-evento", url = "${MSVC_COMPRAS_URL:http://localhost:8003}")
public interface CompradoresEventoClient {
    @GetMapping("/interno/notificaciones/eventos/{id}/compradores")
    List<Long> compradores(@PathVariable("id") Long id, @RequestParam("cancelacion") boolean cancelacion);

    @GetMapping("/interno/notificaciones/eventos/{id}/ventas-por-sector")
    List<Ventas> ventasPorSector(@PathVariable("id") Long id);

    record Ventas(Long idSector, long vendidas, long devueltas, BigDecimal ingresosNetos) {}
}
