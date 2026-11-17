package org.msvcompraentradasol.controllers;

import jakarta.validation.Valid;
import java.util.List;
import org.msvcompraentradasol.dtos.SolicitudRequestDTO;
import org.msvcompraentradasol.dtos.SolicitudResolucionDTO;
import org.msvcompraentradasol.dtos.SolicitudResponseDTO;
import org.msvcompraentradasol.services.SolicitudService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/solicitudes")
public class SolicitudController {

    private final SolicitudService solicitudService;

    public SolicitudController(SolicitudService solicitudService) {
        this.solicitudService = solicitudService;
    }

    @PostMapping
    public ResponseEntity<SolicitudResponseDTO> crearSolicitud(
            @Valid @RequestBody SolicitudRequestDTO dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(solicitudService.crearSolicitud(dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SolicitudResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(solicitudService.buscarPorId(id));
    }

    @GetMapping("/entrada/{idEntrada}")
    public ResponseEntity<List<SolicitudResponseDTO>> listarPorEntrada(
            @PathVariable Long idEntrada
    ) {
        return ResponseEntity.ok(solicitudService.listarPorEntrada(idEntrada));
    }

    @GetMapping("/pendientes")
    public ResponseEntity<List<SolicitudResponseDTO>> listarPendientes() {
        return ResponseEntity.ok(solicitudService.listarPendientes());
    }

    @GetMapping("/cliente/{idCliente}")
    public ResponseEntity<List<SolicitudResponseDTO>> listarPorCliente(
            @PathVariable Long idCliente
    ) {
        return ResponseEntity.ok(solicitudService.listarPorCliente(idCliente));
    }

    @PutMapping("/{id}/resolver")
    public ResponseEntity<SolicitudResponseDTO> resolverSolicitud(
            @PathVariable Long id,
            @Valid @RequestBody SolicitudResolucionDTO dto
    ) {
        return ResponseEntity.ok(solicitudService.resolverSolicitud(
                id,
                dto.getIdAdministrador(),
                dto.getEstadoSolicitud()
        ));
    }
}
