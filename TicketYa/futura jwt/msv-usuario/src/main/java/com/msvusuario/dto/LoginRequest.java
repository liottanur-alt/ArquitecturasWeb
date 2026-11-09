package com.msvusuario.dto;

public record LoginRequest(String email, String password) {
    @Override
    public String toString() { return "LoginRequest[credenciales ocultas]"; }
}
