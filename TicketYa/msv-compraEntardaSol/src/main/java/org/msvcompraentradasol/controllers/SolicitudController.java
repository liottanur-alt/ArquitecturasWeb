package org.msvcompraentradasol.controllers;

import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.msvcompraentradasol.services.SolicitudService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.msvcompraentradasol.entities.Solicitud;
import org.msvcompraentradasol.dtos.SolicitudRequestDTO;
import org.msvcompraentradasol.dtos.SolicitudResolucionDTO;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;

@RestController
@RequestMapping("/solicitudes")
public class SolicitudController {

    @Autowired
    private SolicitudService solicitudService;

    @PostMapping
    public ResponseEntity<Solicitud> crearSolicitud(@Valid @RequestBody SolicitudRequestDTO dto) {
        Solicitud solicitud = solicitudService.crearSolicitud(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(solicitud);
    }

    @GetMapping("/entrada/{idEntrada}")
    public ResponseEntity<List<Solicitud>> listarPorEntrada(@PathVariable Long idEntrada) {
        return ResponseEntity.ok(solicitudService.listarPorEntrada(idEntrada));
    }

    @GetMapping("/pendientes")
    public ResponseEntity<List<Solicitud>> listarPendientes() {
        return ResponseEntity.ok(solicitudService.listarPendientes());
    }

    @PutMapping("/{id}/resolver")
    public ResponseEntity<Solicitud> resolverSolicitud(
            @PathVariable Long id,
            @Valid @RequestBody SolicitudResolucionDTO dto) {

        Solicitud solicitud = solicitudService.resolverSolicitud(
                id,
                dto.getIdAdministrador(),
                dto.getEstadoSolicitud()
        );

        return ResponseEntity.ok(solicitud);
    }

}
