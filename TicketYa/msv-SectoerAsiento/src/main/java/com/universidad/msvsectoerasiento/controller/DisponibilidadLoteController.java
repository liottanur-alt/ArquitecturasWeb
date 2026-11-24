package com.universidad.msvsectoerasiento.controller;

import com.universidad.msvsectoerasiento.dto.DisponibilidadEventoResponse;
import com.universidad.msvsectoerasiento.dto.DisponibilidadLoteRequest;
import com.universidad.msvsectoerasiento.repository.SectorEventoRepository;
import com.universidad.msvsectoerasiento.service.InventarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class DisponibilidadLoteController {
    private final InventarioService inventario;
    private final SectorEventoRepository sectores;

    @PostMapping("/interno/inventario/disponibilidad")
    public ResponseEntity<List<DisponibilidadEventoResponse>> consultar(@Valid @RequestBody DisponibilidadLoteRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(inventario.consultarLote(request));
    }

    @PostMapping("/interno/inventario/precios-minimos")
    public Map<Long, BigDecimal> preciosMinimos(@Valid @RequestBody DisponibilidadLoteRequest request) {
        if (request.idsEventos().isEmpty()) {
            return Map.of();
        }
        return sectores.preciosMinimos(request.idsEventos()).stream()
                .collect(Collectors.toMap(SectorEventoRepository.PrecioEvento::getIdEvento,
                        SectorEventoRepository.PrecioEvento::getPrecio));
    }
}
