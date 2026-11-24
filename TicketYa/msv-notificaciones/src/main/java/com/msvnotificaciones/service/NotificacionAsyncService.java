package com.msvnotificaciones.service;

import com.msvnotificaciones.entity.*;
import com.msvnotificaciones.repository.NotificacionRepository;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Service
public class NotificacionAsyncService {
    private static final Logger log = LoggerFactory.getLogger(NotificacionAsyncService.class);
    private final NotificacionRepository notificaciones;
    private final CorreoService correo;

    public NotificacionAsyncService(NotificacionRepository notificaciones, CorreoService correo) {
        this.notificaciones = notificaciones;
        this.correo = correo;
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
            case EVENTO_MODIFICADO -> {
                asunto = "Evento modificado";
                mensaje = "Cambió la programación del evento " + s.getIdEvento() + ".";
            }
            case ARTISTA_FAVORITO_PUBLICADO -> {
                asunto = "Un artista favorito toca en tu ciudad";
                mensaje = "Un artista que seguís participa en un nuevo evento en tu ciudad.";
            }
            default -> throw new IllegalStateException("Tipo de suceso sin plantilla: " + s.getTipo());
        }
        if (s.getDetalle() != null && !s.getDetalle().isBlank()) {
            mensaje += " " + s.getDetalle();
        }
        if (s.getDestinatario() == null) {
            notificaciones.save(new Notificacion(s.getId(), s.getIdCliente(), null,
                asunto, mensaje, EstadoNotificacion.OMITIDA));
            return;
        }

        Notificacion notificacion = notificaciones.save(new Notificacion(s.getId(), s.getIdCliente(),
            s.getDestinatario(), asunto, mensaje, EstadoNotificacion.PENDIENTE));
        try {
            correo.enviar(notificacion);
            notificacion.setEstado(EstadoNotificacion.ENVIADA);
        } catch (MessagingException | RuntimeException e) {
            log.warn("Falló el envío de la notificación {} ({})", notificacion.getId(),
                e.getClass().getSimpleName());
            notificacion.setEstado(EstadoNotificacion.FALLIDA);
        }
        notificaciones.save(notificacion);
    }

    public record SucesoRegistrado(SucesoNegocio suceso) {}
}
