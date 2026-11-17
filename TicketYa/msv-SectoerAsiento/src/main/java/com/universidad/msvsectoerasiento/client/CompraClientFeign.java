package com.universidad.msvsectoerasiento.client;

import com.universidad.msvsectoerasiento.client.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;


@FeignClient(name = "msvc-compras", url = "${msvc.compras.url}")
public interface CompraClientFeign {
    @GetMapping("/compras/{id}")
    CompraResponse obtenerCompra(@PathVariable("id") Long id);

    @GetMapping("/entradas/{id}")
    EntradaResponse obtenerEntrada(@PathVariable("id") Long id);
}
