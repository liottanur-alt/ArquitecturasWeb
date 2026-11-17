package com.universidad.msvsectoerasiento.client.dto;

public record CompraResponse(Long id, Long idCliente, Long idEvento, String medioDePago,
                             String descripcion, Long cantidadEntradas) {
}
