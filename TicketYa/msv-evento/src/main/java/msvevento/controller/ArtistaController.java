
package msvevento.controller;

import msvevento.dto.ArtistaRequestDTO;
import msvevento.dto.ArtistaResponseDTO;
import msvevento.service.ArtistaService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/artistas")
public class ArtistaController {

    private final ArtistaService artistaService;

    public ArtistaController(ArtistaService artistaService) {
        this.artistaService = artistaService;
    }

    // Listar todos los artistas
    @GetMapping
    public ResponseEntity<List<ArtistaResponseDTO>> listarArtistas() {

        return ResponseEntity.ok(artistaService.listarArtistas());
    }

    // Buscar un artista por ID
    @GetMapping("/{id}")
    public ResponseEntity<ArtistaResponseDTO> buscarArtistaPorId(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(artistaService.buscarArtistaPorId(id));
    }

    // Crear un artista
    @PostMapping
    public ResponseEntity<ArtistaResponseDTO> crearArtista(
            @RequestBody ArtistaRequestDTO dto
    ) {

        ArtistaResponseDTO artistaCreado = artistaService.crearArtista(dto);

        return ResponseEntity.status(HttpStatus.CREATED).body(artistaCreado);
    }

    // Actualizar un artista
    @PutMapping("/{id}")
    public ResponseEntity<ArtistaResponseDTO> actualizarArtista(
            @PathVariable Long id,
            @RequestBody ArtistaRequestDTO dto
    ) {

        return ResponseEntity.ok(artistaService.actualizarArtista(id, dto));
    }

    // Eliminar un artista
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarArtista(@PathVariable Long id) {

        artistaService.eliminarArtista(id);

        return ResponseEntity.noContent().build();
    }
}
