package com.msvusuario.client;

import com.msvusuario.dto.CompraEnComunResponse;
import com.msvusuario.dto.HistorialValidoResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "compras-recomendaciones", url = "${msv.compras.url}")
public interface CompraClientFeign {
    @GetMapping("/interno/recomendaciones/clientes/{idCliente}")
    List<CompraEnComunResponse> recomendar(@PathVariable("idCliente") Long idCliente);

    @GetMapping("/interno/recomendaciones/clientes/{idCliente}/historial-valido")
    HistorialValidoResponse historialValido(@PathVariable("idCliente") Long idCliente);
}
