package msvevento.service;

import msvevento.client.CompradoresEventoClient;
import msvevento.client.DestinatarioEventoClient;
import msvevento.client.DestinatarioEventoClient.Destinatario;
import msvevento.client.NotificacionEventoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

@Component
public class NotificarEvento {
    private static final Logger log = LoggerFactory.getLogger(NotificarEvento.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final CompradoresEventoClient compradores;
    private final DestinatarioEventoClient destinatarios;
    private final NotificacionEventoClient notificaciones;

    public NotificarEvento(CompradoresEventoClient compradores, DestinatarioEventoClient destinatarios,
                           NotificacionEventoClient notificaciones) {
        this.compradores = compradores;
        this.destinatarios = destinatarios;
        this.notificaciones = notificaciones;
    }

    @TransactionalEventListener
    @Async
    public void notificarPublicacion(Publicado evento) {
        var consulta = new DestinatarioEventoClient.Favoritos(evento.ciudad(), evento.artistas());
        String detalle = evento.nombre() + " · " + evento.ciudad() + " · " + fecha(evento.fechaHora());
        var aviso = new Cambio(evento.id(), "artista-favorito-" + evento.id(), "ARTISTA_FAVORITO_PUBLICADO", detalle);
        intentar(evento.id(), () -> destinatarios.porFavoritos(consulta)
                .forEach(destinatario -> registrar(aviso, () -> destinatario)));
    }

    @TransactionalEventListener
    @Async
    public void notificarCambio(Cambio evento) {
        intentar(evento.id(), () -> compradores.compradores(evento.id(), "EVENTO_CANCELADO".equals(evento.tipo()))
                .forEach(idCliente -> registrar(evento, () -> destinatarios.porId(idCliente))));
    }

    private void registrar(Cambio evento, Supplier<Destinatario> buscarDestinatario) {
        intentar(evento.id(), () -> {
            Destinatario destinatario = buscarDestinatario.get();
            notificaciones.registrar(new NotificacionEventoClient.Suceso(
                    evento.clave() + "-" + destinatario.id(), evento.tipo(), evento.id(),
                    destinatario.id(), destinatario.email(), evento.detalle()));
        });
    }

    private void intentar(Long idEvento, Runnable envio) {
        try {
            envio.run();
        } catch (RuntimeException error) {
            log.warn("No se pudo registrar un aviso del evento {} ({})", idEvento, error.getClass().getSimpleName());
        }
    }

    static String fecha(LocalDateTime fecha) {
        return fecha == null ? "sin fecha" : FECHA.format(fecha);
    }

    public record Publicado(Long id, String nombre, String ciudad, LocalDateTime fechaHora, List<Long> artistas) {}
    public record Cambio(Long id, String clave, String tipo, String detalle) {}
}
