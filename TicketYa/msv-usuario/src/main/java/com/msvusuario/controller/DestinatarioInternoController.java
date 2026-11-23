package com.msvusuario.controller;

import com.msvusuario.entity.Usuario;
import com.msvusuario.entity.TipoRol;
import com.msvusuario.repository.UsuarioRepository;
import com.msvusuario.service.RecomendacionPreferenciasService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/interno/notificaciones/destinatarios")
@PreAuthorize("hasRole('SERVICIO_INTERNO')")
public class DestinatarioInternoController {
    private final UsuarioRepository usuarios;

    public DestinatarioInternoController(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping("/{id}")
    public Destinatario porId(@PathVariable Long id) {
        return usuarios.findById(id).map(Destinatario::de)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/favoritos")
    public List<Destinatario> porFavoritos(@RequestBody Favoritos consulta) {
        String ciudad = RecomendacionPreferenciasService.normalizarCiudad(consulta.ciudad());
        if (ciudad.isEmpty() || consulta.artistas() == null || consulta.artistas().isEmpty()) {
            return List.of();
        }
        return usuarios.findByArtistasFavoritosIn(consulta.artistas()).stream()
                .filter(usuario -> usuario.getRol() != null && usuario.getRol().getName() == TipoRol.CLIENTE)
                .filter(usuario -> ciudad.equals(RecomendacionPreferenciasService.normalizarCiudad(usuario.getCiudad())))
                .map(Destinatario::de)
                .toList();
    }

    public record Favoritos(String ciudad, List<Long> artistas) {}

    public record Destinatario(Long id, String email) {
        static Destinatario de(Usuario usuario) {
            return new Destinatario(usuario.getId(), usuario.getEmail());
        }
    }
}
