package com.msvusuario.dto;

public record RegistroRequest(String nombre, String email, String dni, String ciudad, String password) {
    @Override
    public String toString() { return "RegistroRequest[credenciales ocultas]"; }
}
