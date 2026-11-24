package org.msvcompraentradasol.services;

import org.msvcompraentradasol.dtos.CompraResponseDTO;
import org.msvcompraentradasol.feigns.DestinatarioCompraClient;
import org.msvcompraentradasol.feigns.NotificacionCompraClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificarCompraConfirmada {
    private static final Logger log = LoggerFactory.getLogger(NotificarCompraConfirmada.class);
    private final DestinatarioCompraClient destinatarios;
    private final NotificacionCompraClient notificaciones;

    public NotificarCompraConfirmada(DestinatarioCompraClient destinatarios, NotificacionCompraClient notificaciones) {
        this.destinatarios = destinatarios;
        this.notificaciones = notificaciones;
    }

    @TransactionalEventListener
    public void notificar(CompraConfirmada evento) {
        var compra = evento.compra();
        try {
            var cliente = destinatarios.porId(compra.idCliente());
            String detalle = evento.nombreEvento() == null ? null : "Evento: " + evento.nombreEvento() + ".";
            notificaciones.registrar(new NotificacionCompraClient.Suceso(
                    "compra-confirmada-" + compra.id(), "COMPRA_CONFIRMADA", compra.idEvento(),
                    compra.id(), compra.idCliente(), cliente.email(), compra.cantidadEntradas(), compra.importeTotal(),
                    detalle));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar el aviso de la compra {} ({})", compra.id(), e.getClass().getSimpleName());
        }
    }

    public record CompraConfirmada(CompraResponseDTO compra, String nombreEvento) {}

    @TransactionalEventListener
    public void notificarResolucion(NotificacionCompraClient.Suceso suceso) {
        try {
            notificaciones.registrar(suceso);
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar el aviso {} ({})", suceso.claveIdempotencia(), e.getClass().getSimpleName());
        }
    }
}
