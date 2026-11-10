package com.universidad.msvsectoerasiento.controller;

import com.universidad.msvsectoerasiento.dto.*;
import com.universidad.msvsectoerasiento.service.AsientoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.List;

@RestController
@RequestMapping("/asientos")
@RequiredArgsConstructor
public class AsientoController {
    private final AsientoService asientoService;

    @GetMapping
    public List<AsientoResponse> listar(@RequestParam(required = false) @Positive Long idSector) {
        return asientoService.listar(idSector);
    }

    @GetMapping("/{id}")
    public AsientoResponse obtener(@PathVariable @Positive Long id) {
        return asientoService.obtener(id);
    }

    @PostMapping
    public ResponseEntity<AsientoResponse> crear(@Valid @RequestBody AsientoRequest request) {
        AsientoResponse response = asientoService.crear(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public AsientoResponse actualizar(@PathVariable @Positive Long id,@Valid @RequestBody AsientoRequest request) {
        return asientoService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable @Positive Long id) {
        asientoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
