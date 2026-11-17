package com.msvnotificaciones.service;

import com.msvnotificaciones.entity.TipoSuceso;
import com.msvnotificaciones.dto.ReporteEventoResponse;
import com.msvnotificaciones.repository.SucesoNegocioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@Transactional(readOnly = true)
public class ReporteService {
    private final SucesoNegocioRepository sucesos;
    public ReporteService(SucesoNegocioRepository sucesos) { this.sucesos = sucesos; }

    public ReporteEventoResponse porEvento(Long idEvento) {
        long vendidas = valor(sucesos.totalEntradas(idEvento, TipoSuceso.COMPRA_CONFIRMADA));
        long devueltas = valor(sucesos.totalEntradas(idEvento, TipoSuceso.DEVOLUCION_APROBADA));
        BigDecimal ventas = importe(sucesos.totalImporte(idEvento, TipoSuceso.COMPRA_CONFIRMADA));
        BigDecimal devoluciones = importe(sucesos.totalImporte(idEvento, TipoSuceso.DEVOLUCION_APROBADA));
        Integer capacidad = sucesos.capacidad(idEvento);
        long activas = Math.max(0, vendidas - devueltas);
        Double ocupacion = capacidad == null ? null : Math.min(100.0, activas * 100.0 / capacidad);
        return new ReporteEventoResponse(idEvento, vendidas, devueltas, ventas.subtract(devoluciones), capacidad, ocupacion);
    }
    private long valor(Long valor) { return valor == null ? 0 : valor; }
    private BigDecimal importe(BigDecimal valor) { return valor == null ? BigDecimal.ZERO : valor; }
}
