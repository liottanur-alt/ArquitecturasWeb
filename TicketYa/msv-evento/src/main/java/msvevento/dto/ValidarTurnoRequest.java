package msvevento.dto;

public record ValidarTurnoRequest(
        Long eventoId,
        Long clienteId,
        String autorizacionCompra,
        String idOperacion
) {}
