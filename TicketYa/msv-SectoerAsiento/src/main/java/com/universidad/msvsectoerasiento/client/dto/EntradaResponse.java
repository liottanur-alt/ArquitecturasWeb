package com.universidad.msvsectoerasiento.client.dto;

import java.math.BigDecimal;

/** idAsiento es null para entradas de campo. Estados pertenecen al servicio de compras. */
public record EntradaResponse(Long id, String qr, String estadoQr, Long idCliente,
                              Long idEvento, Long idSector, Long idAsiento,
                              BigDecimal precio, String estadoEntrada) {
}
