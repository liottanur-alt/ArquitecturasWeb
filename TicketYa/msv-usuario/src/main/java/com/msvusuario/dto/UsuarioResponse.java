package com.msvusuario.dto;

import com.msvusuario.entity.Usuario;

public record UsuarioResponse(Long id, String nombre, String email, String dni, String ciudad, Long idRol) {
    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail(),
                usuario.getDni(), usuario.getCiudad(), usuario.getRol().getId());
    }
}
