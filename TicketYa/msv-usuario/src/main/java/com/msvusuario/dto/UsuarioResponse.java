package com.msvusuario.dto;

import com.msvusuario.entity.Usuario;
import com.msvusuario.entity.TipoEvento;
import java.util.Set;

public record UsuarioResponse(Long id, String nombre, String email, String dni, String ciudad, Long idRol,
                              Set<TipoEvento> tiposEventoPreferidos, String ciudadPreferida) {
    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail(),
                usuario.getDni(), usuario.getCiudad(), usuario.getRol().getId(),
                usuario.getTiposEventoPreferidos() == null ? Set.of() : Set.copyOf(usuario.getTiposEventoPreferidos()),
                usuario.getCiudadPreferida() == null ? "" : usuario.getCiudadPreferida());
    }
}
