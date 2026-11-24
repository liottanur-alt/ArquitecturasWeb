package msvevento.dto;

public record ValidarTurnoResponse(
        boolean autorizado,
        boolean requiereFila,
        String motivo
) {}
