package com.msvusuario.dto;

// Contrato interno de Compras: solo eventos y cantidades agregadas, sin identidades.
public record CompraEnComunResponse(Long idEvento, Long compradoresEnComun, Long idEventoReferencia) {}
