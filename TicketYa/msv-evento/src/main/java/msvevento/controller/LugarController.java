
package msvevento.controller;

import msvevento.dto.LugarRequestDTO;
import msvevento.dto.LugarResponseDTO;
import msvevento.service.LugarService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lugares")
public class LugarController {

    private final LugarService lugarService;

    public LugarController(LugarService lugarService) {
        this.lugarService = lugarService;
    }

    // Listar todos los lugares
    @GetMapping
    public ResponseEntity<List<LugarResponseDTO>> listarLugares() {

        return ResponseEntity.ok(lugarService.listarLugares());
    }

    // Buscar un lugar por ID
    @GetMapping("/{id}")
    public ResponseEntity<LugarResponseDTO> buscarLugarPorId(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(lugarService.buscarLugarPorId(id));
    }

    // Crear un lugar
    @PostMapping
    public ResponseEntity<LugarResponseDTO> crearLugar(
            @RequestBody LugarRequestDTO dto
    ) {

        LugarResponseDTO lugarCreado = lugarService.crearLugar(dto);

        return ResponseEntity.status(HttpStatus.CREATED).body(lugarCreado);
    }

    // Actualizar un lugar
    @PutMapping("/{id}")
    public ResponseEntity<LugarResponseDTO> actualizarLugar(
            @PathVariable Long id,
            @RequestBody LugarRequestDTO dto
    ) {

        return ResponseEntity.ok(lugarService.actualizarLugar(id, dto));
    }

    // Eliminar un lugar
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarLugar(@PathVariable Long id) {

        lugarService.eliminarLugar(id);

        return ResponseEntity.noContent().build();
    }
}
