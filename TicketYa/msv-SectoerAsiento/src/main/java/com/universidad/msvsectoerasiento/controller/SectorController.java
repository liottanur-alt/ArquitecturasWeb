package com.universidad.msvsectoerasiento.controller;

import com.universidad.msvsectoerasiento.dto.*;
import com.universidad.msvsectoerasiento.service.SectorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.List;

@RestController
@RequestMapping("/sectores")
@RequiredArgsConstructor
public class SectorController {
    private final SectorService sectorService;

    @GetMapping
    public List<SectorResponse> listar(@RequestParam(required = false) @Positive Long idLugar) {
        return sectorService.listar(idLugar);
    }

    @GetMapping("/{id}")
    public SectorResponse obtener(@PathVariable @Positive Long id) {
        return sectorService.obtener(id);
    }

    @PostMapping
    public ResponseEntity<SectorResponse> crear(@Valid @RequestBody SectorRequest request) {
        SectorResponse response = sectorService.crear(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public SectorResponse actualizar(@PathVariable @Positive Long id,@Valid @RequestBody SectorRequest request) {
        return sectorService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable @Positive Long id) {
        sectorService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
