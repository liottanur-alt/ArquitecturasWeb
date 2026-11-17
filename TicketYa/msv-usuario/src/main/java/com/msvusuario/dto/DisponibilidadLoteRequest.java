package com.msvusuario.dto;

import java.util.List;

public record DisponibilidadLoteRequest(List<Long> idsEventos) {
    public DisponibilidadLoteRequest { idsEventos = List.copyOf(idsEventos); }
}
