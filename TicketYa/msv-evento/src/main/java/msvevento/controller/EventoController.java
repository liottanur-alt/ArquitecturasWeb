
package msvevento.controller;

import msvevento.dto.EventoRequestDTO;
import msvevento.dto.EventoResponseDTO;
import msvevento.entity.TipoEvento;
import msvevento.service.EventoService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/eventos")
public class EventoController {

    private final EventoService eventoService;

    public EventoController(EventoService eventoService) {
        this.eventoService = eventoService;
    }

    // Listar todos los eventos
    @GetMapping
    public ResponseEntity<List<EventoResponseDTO>> listarEventos() {

        return ResponseEntity.ok(eventoService.listarEventos());
    }

    // Buscar un evento por ID
    @GetMapping("/{id}")
    public ResponseEntity<EventoResponseDTO> buscarEventoPorId(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(eventoService.buscarEventoPorId(id));
    }

    // Crear un evento
    @PostMapping
    public ResponseEntity<EventoResponseDTO> crearEvento(
            @RequestBody EventoRequestDTO dto
    ) {

        EventoResponseDTO eventoCreado = eventoService.crearEvento(dto);

        return ResponseEntity.status(HttpStatus.CREATED).body(eventoCreado);
    }

    // Actualizar un evento
    @PutMapping("/{id}")
    public ResponseEntity<EventoResponseDTO> actualizarEvento(
            @PathVariable Long id,
            @RequestBody EventoRequestDTO dto
    ) {

        return ResponseEntity.ok(eventoService.actualizarEvento(id, dto));
    }
    // Publicar un evento
    @PatchMapping("/{id}/publicar")
    public ResponseEntity<EventoResponseDTO> publicarEvento(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(eventoService.publicarEvento(id));
    }


    // Suspender un evento
    @PatchMapping("/{id}/suspender")
    public ResponseEntity<EventoResponseDTO> suspenderEvento(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(eventoService.suspenderEvento(id));
    }


    // Cancelar un evento
    @PatchMapping("/{id}/cancelar")
    public ResponseEntity<EventoResponseDTO> cancelarEvento(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(eventoService.cancelarEvento(id));
    }

    // Buscar eventos aplicando filtros
    @GetMapping("/buscar")
    public ResponseEntity<List<EventoResponseDTO>> buscarEventos(
            @RequestParam(required = false) TipoEvento tipo,
            @RequestParam(required = false) Long idOrganizador,
            @RequestParam(required = false) Long idParticipante,
            @RequestParam(required = false) String ciudad,
            @RequestParam(required = false)
            LocalDateTime fechaDesde,
            @RequestParam(required = false)
            LocalDateTime fechaHasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanio,
            @RequestParam(defaultValue = "fechaHora,asc") String ordenar
    ) {

        return ResponseEntity.ok(
                eventoService.buscarEventos(
                        tipo,
                        idOrganizador,
                        idParticipante,
                        ciudad,
                        fechaDesde,
                        fechaHasta,
                        pagina,
                        tamanio,
                        ordenar
                )
        );
    }

    // Eliminar un evento
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarEvento(@PathVariable Long id) {

        eventoService.eliminarEvento(id);

        return ResponseEntity.noContent().build();
    }
}
