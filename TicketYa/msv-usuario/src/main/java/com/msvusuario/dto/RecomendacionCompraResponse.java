package com.msvusuario.dto;

import java.time.LocalDateTime;

public record RecomendacionCompraResponse(Long idEvento, String nombre, String tipo, LocalDateTime fechaHora,
                                         String ciudad, long compradoresEnComun, String motivo) {}
