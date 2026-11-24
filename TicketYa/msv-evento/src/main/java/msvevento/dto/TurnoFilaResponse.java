package msvevento.dto;

import java.time.Instant;

public record TurnoFilaResponse(
        Long eventoId,
        String turnoId,
        String estado,
        Long posicion,
        String autorizacionCompra,
        Instant venceEn
) {}
