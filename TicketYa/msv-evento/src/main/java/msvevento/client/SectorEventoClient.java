package msvevento.client;

import msvevento.dto.SectorEventoResponseDTO;
import msvevento.dto.EventosPorIdsRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@FeignClient(name = "msvc-sectores", url = "${msvc.sectores.url}")
public interface SectorEventoClient {
    @GetMapping("/eventos/{idEvento}/sectores")
    List<SectorEventoResponseDTO> listarConfigurados(@PathVariable("idEvento") Long idEvento);

    @PostMapping("/interno/inventario/precios-minimos")
    Map<Long, BigDecimal> preciosMinimos(@RequestBody EventosPorIdsRequest request);
}
