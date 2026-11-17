package com.msvnotificaciones.service;

import com.msvnotificaciones.entity.*;
import com.msvnotificaciones.repository.NotificacionRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Service
public class NotificacionAsyncService {
    private final NotificacionRepository notificaciones;

    public NotificacionAsyncService(NotificacionRepository notificaciones) {
        this.notificaciones = notificaciones;
    }

    @Async("notificacionesExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void procesar(SucesoRegistrado registrado) {
        SucesoNegocio s = registrado.suceso();
        if (s.getIdCliente() == null && (s.getDestinatario() == null || s.getDestinatario().isBlank())) {
            notificaciones.save(new Notificacion(s.getId(), null, null,
                "Aviso de TicketYa", "El suceso fue registrado, pero no tiene destinatario.", EstadoNotificacion.OMITIDA));
            return;
        }
        String asunto;
        String mensaje;
        switch (s.getTipo()) {
            case COMPRA_CONFIRMADA -> {
                asunto = "Compra confirmada";
                mensaje = "Tu compra " + s.getIdCompra() + " para el evento " + s.getIdEvento()
                    + " fue confirmada. Entradas: " + s.getCantidadEntradas() + ".";
            }
            case DEVOLUCION_APROBADA -> {
                asunto = "Devolución aprobada";
                mensaje = "La devolución de la compra " + s.getIdCompra() + " para el evento "
                    + s.getIdEvento() + " fue aprobada.";
            }
            case EVENTO_CANCELADO -> {
                asunto = "Evento cancelado";
                mensaje = "El evento " + s.getIdEvento() + " fue cancelado. Revisá la información de tu compra.";
            }
            default -> throw new IllegalStateException("Tipo de suceso sin plantilla: " + s.getTipo());
        }
        notificaciones.save(new Notificacion(s.getId(), s.getIdCliente(), s.getDestinatario(),
            asunto, mensaje, EstadoNotificacion.ENVIADA));
    }

    public record SucesoRegistrado(SucesoNegocio suceso) {}
}
