package com.msvnotificaciones.dto;

import java.math.BigDecimal;

public record ReporteEventoResponse(Long idEvento, long entradasVendidas,
                                   long entradasDevueltas, BigDecimal ingresosNetos,
                                   Integer capacidadTotal, Double ocupacionPorcentaje) {}
