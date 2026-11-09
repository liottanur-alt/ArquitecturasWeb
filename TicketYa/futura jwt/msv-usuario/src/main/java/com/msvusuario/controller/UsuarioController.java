package com.msvusuario.controller;

import com.msvusuario.dto.UsuarioRequest;
import com.msvusuario.dto.UsuarioResponse;
import com.msvusuario.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {
    private final UsuarioService service;

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public List<UsuarioResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR') or #id.toString() == authentication.name")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<UsuarioResponse> crear(@RequestBody UsuarioRequest request) {
        UsuarioResponse usuario = service.crear(request);
        return ResponseEntity.created(URI.create("/usuarios/" + usuario.id())).body(usuario);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@usuarioAccess.puedeActualizar(#id, #request, authentication)")
    public UsuarioResponse actualizar(@PathVariable Long id, @RequestBody UsuarioRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR') or #id.toString() == authentication.name")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/artistas-favoritos")
    @PreAuthorize("hasRole('ADMINISTRADOR') or #id.toString() == authentication.name")
    public List<Long> listarFavoritos(@PathVariable Long id) {
        return service.listarFavoritos(id);
    }

    @PutMapping("/{id}/artistas-favoritos/{idArtista}")
    @PreAuthorize("hasRole('ADMINISTRADOR') or #id.toString() == authentication.name")
    public ResponseEntity<Void> agregarFavorito(@PathVariable Long id, @PathVariable Long idArtista) {
        service.agregarFavorito(id, idArtista);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/artistas-favoritos/{idArtista}")
    @PreAuthorize("hasRole('ADMINISTRADOR') or #id.toString() == authentication.name")
    public ResponseEntity<Void> eliminarFavorito(@PathVariable Long id, @PathVariable Long idArtista) {
        service.eliminarFavorito(id, idArtista);
        return ResponseEntity.noContent().build();
    }
}
