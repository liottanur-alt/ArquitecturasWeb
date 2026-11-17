package msvevento.client;

import msvevento.dto.SectorEventoResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "msvc-sectores", url = "${msvc.sectores.url}")
public interface SectorEventoClient {
    @GetMapping("/eventos/{idEvento}/sectores")
    List<SectorEventoResponseDTO> listarConfigurados(@PathVariable("idEvento") Long idEvento);
}
