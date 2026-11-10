package org.msvcompraentradasol.services;

import java.util.List;
import java.util.Objects;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.msvcompraentradasol.dtos.DevolucionRequestDTO;
import org.msvcompraentradasol.dtos.SolicitudRequestDTO;
import org.msvcompraentradasol.entities.Compra;
import org.msvcompraentradasol.entities.Entrada;
import org.msvcompraentradasol.entities.Solicitud;
import org.msvcompraentradasol.enums.EstadoEntrada;
import org.msvcompraentradasol.enums.EstadoSolicitud;
import org.msvcompraentradasol.feigns.EventoClientFeign;
import org.msvcompraentradasol.feigns.ReservaClientFeign;
import org.msvcompraentradasol.repositories.EntradaRepository;
import org.msvcompraentradasol.repositories.SolicitudRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SolicitudService {

    private static final ZoneId ZONA_EVENTOS = ZoneId.of("America/Argentina/Buenos_Aires");

    private final SolicitudRepository solicitudRepository;
    private final EntradaRepository entradaRepository;
    private final EventoClientFeign eventoClient;
    private final ReservaClientFeign reservaClient;
    private final PagoSimuladoService pagoSimuladoService;

    public SolicitudService(
            SolicitudRepository solicitudRepository,
            EntradaRepository entradaRepository,
            EventoClientFeign eventoClient,
            ReservaClientFeign reservaClient,
            PagoSimuladoService pagoSimuladoService
    ) {
        this.solicitudRepository = solicitudRepository;
        this.entradaRepository = entradaRepository;
        this.eventoClient = eventoClient;
        this.reservaClient = reservaClient;
        this.pagoSimuladoService = pagoSimuladoService;
    }

    @Transactional
    public Solicitud crearSolicitud(SolicitudRequestDTO dto) {
        Long idEntrada = dto.getIdEntrada();
        Entrada entrada = entradaRepository.findById(idEntrada)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrada no encontrada"));

        if (!Objects.equals(entrada.getIdCliente(), dto.getIdCliente())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La entrada no pertenece al cliente indicado");
        }

        if (entrada.getEstadoEntrada() != EstadoEntrada.PAGA
                && entrada.getEstadoEntrada() != EstadoEntrada.DEVOLUCION_RECHAZADA) {
            throw new IllegalStateException("La entrada no está disponible para solicitar una devolución");
        }

        boolean pendiente = solicitudRepository.existsByEntrada_IdAndEstadoSolicitud(
                idEntrada,
                EstadoSolicitud.PENDIENTE
        );
        boolean aceptada = solicitudRepository.existsByEntrada_IdAndEstadoSolicitud(
                idEntrada,
                EstadoSolicitud.ACEPTADA
        );

        if (pendiente || aceptada) {
            throw new IllegalStateException("No se puede realizar una nueva solicitud");
        }

        if (entrada.getIdEvento() == null) {
            throw new IllegalStateException("La entrada no tiene un evento asociado");
        }
        var evento = eventoClient.obtenerFecha(entrada.getIdEvento());
        if (evento.fechaHora() == null) {
            throw new IllegalStateException("El evento no tiene una fecha configurada");
        }
        if (evento.fechaHora().isBefore(LocalDateTime.now(ZONA_EVENTOS).plusHours(72))) {
            throw new IllegalStateException("La devolución debe solicitarse al menos 72 horas antes del evento");
        }

        Solicitud solicitud = new Solicitud();
        solicitud.setEntrada(entrada);
        solicitud.setIdCliente(dto.getIdCliente());
        solicitud.setFechaSolicitud(Instant.now());
        solicitud.setDescripcion(dto.getDescripcion());
        solicitud.setEstadoSolicitud(EstadoSolicitud.PENDIENTE);

        entrada.setEstadoEntrada(EstadoEntrada.SOLICITUD_DEVOLUCION);
        entradaRepository.save(entrada);

        return solicitudRepository.save(solicitud);
    }

    @Transactional(readOnly = true)
    public List<Solicitud> listarPorEntrada(Long idEntrada) {
        return solicitudRepository.findAllByEntrada_IdOrderByIdAsc(idEntrada);
    }

    @Transactional(readOnly = true)
    public List<Solicitud> listarPendientes() {
        return solicitudRepository.findAllByEstadoSolicitudOrderByIdAsc(EstadoSolicitud.PENDIENTE);
    }

    @Transactional
    public Solicitud resolverSolicitud(
            Long idSolicitud,
            Long idAdministrador,
            EstadoSolicitud estado
    ) {
        if (estado != EstadoSolicitud.ACEPTADA && estado != EstadoSolicitud.RECHAZADA) {
            throw new IllegalArgumentException("Estado de resolución inválido");
        }

        Solicitud solicitud = solicitudRepository.findById(idSolicitud)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));

        if (solicitud.getEstadoSolicitud() != EstadoSolicitud.PENDIENTE) {
            throw new IllegalStateException("La solicitud ya fue resuelta");
        }

        solicitud.setIdAdministrador(idAdministrador);
        solicitud.setFechaResolucion(Instant.now());
        solicitud.setEstadoSolicitud(estado);

        Entrada entrada = solicitud.getEntrada();
        if (entrada == null) {
            throw new IllegalStateException("La solicitud no tiene una entrada asociada");
        }
        if (estado == EstadoSolicitud.ACEPTADA) {
            Compra compra = entrada.getCompra();
            if (compra == null || compra.getIdReserva() == null) {
                throw new IllegalStateException("La compra no tiene una reserva asociada");
            }
            var reintegro = pagoSimuladoService.reintegrar(
                    compra.getReferenciaPago(),
                    entrada.getPrecio(),
                    "reintegro-solicitud-" + solicitud.getId()
            );
            if (!reintegro.aprobado()) {
                throw new IllegalStateException("La pasarela simulada rechazó el reintegro");
            }
            solicitud.setReferenciaReintegro(reintegro.referencia());

            List<Long> idAsientos = entrada.getIdAsiento() == null
                    ? List.of()
                    : List.of(entrada.getIdAsiento());
            reservaClient.devolver(
                    compra.getIdReserva(),
                    new DevolucionRequestDTO(
                            "devolucion-solicitud-" + solicitud.getId(),
                            1,
                            Instant.now(),
                            idAsientos
                    )
            );
            entrada.setEstadoEntrada(EstadoEntrada.DEVOLUCION_ACEPTADA);
        } else {
            entrada.setEstadoEntrada(EstadoEntrada.DEVOLUCION_RECHAZADA);
        }
        entradaRepository.save(entrada);

        return solicitudRepository.save(solicitud);
    }
}
