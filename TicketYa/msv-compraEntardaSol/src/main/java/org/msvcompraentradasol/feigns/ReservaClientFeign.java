package org.msvcompraentradasol.feigns;

import org.msvcompraentradasol.dtos.ReservaRequestDTO;
import org.msvcompraentradasol.dtos.ReservaResponseDTO;
import org.msvcompraentradasol.dtos.DevolucionRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "msvc-sectores", url = "${msvc.sectores.url}")
public interface ReservaClientFeign {

    @PostMapping("/reservas")
    ReservaResponseDTO reservar(@RequestBody ReservaRequestDTO solicitud);

    @PostMapping("/reservas/{id}/confirmacion")
    ReservaResponseDTO confirmar(@PathVariable("id") Long idReserva);

    @PostMapping("/reservas/{id}/liberacion")
    ReservaResponseDTO liberar(@PathVariable("id") Long idReserva);

    @PostMapping("/reservas/{id}/devoluciones")
    ReservaResponseDTO devolver(
            @PathVariable("id") Long idReserva,
            @RequestBody DevolucionRequestDTO solicitud
    );

    @PostMapping("/eventos/{id}/inventario/cancelacion")
    void cancelarInventario(@PathVariable("id") Long idEvento);
}
