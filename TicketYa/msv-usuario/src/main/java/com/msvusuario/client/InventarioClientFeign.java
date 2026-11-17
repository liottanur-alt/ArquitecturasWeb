package com.msvusuario.client;

import com.msvusuario.dto.DisponibilidadEventoResponse;
import com.msvusuario.dto.DisponibilidadLoteRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;

@FeignClient(name = "inventario-recomendaciones", url = "${msv.sectores.url}")
public interface InventarioClientFeign {
    @PostMapping("/interno/inventario/disponibilidad")
    List<DisponibilidadEventoResponse> consultar(@RequestBody DisponibilidadLoteRequest request);
}
