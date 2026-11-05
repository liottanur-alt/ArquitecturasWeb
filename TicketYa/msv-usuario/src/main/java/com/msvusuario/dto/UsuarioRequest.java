package com.msvusuario.dto;

public record UsuarioRequest(String nombre, String email, String dni, String ciudad, Long idRol) {
}
