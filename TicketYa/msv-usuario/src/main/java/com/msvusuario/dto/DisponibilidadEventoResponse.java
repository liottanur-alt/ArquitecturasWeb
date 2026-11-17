package com.msvusuario.dto;

// Boolean permite detectar respuestas incompletas, sin confundirlas con un agotado.
public record DisponibilidadEventoResponse(Long idEvento, Boolean disponible) {}
