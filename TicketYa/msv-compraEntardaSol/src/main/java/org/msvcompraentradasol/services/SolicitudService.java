package org.msvcompraentradasol.services;

import feign.FeignException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.msvcompraentradasol.dtos.DevolucionRequestDTO;
import org.msvcompraentradasol.dtos.EventoFechaResponseDTO;
import org.msvcompraentradasol.dtos.ReservaResponseDTO;
import org.msvcompraentradasol.dtos.SolicitudRequestDTO;
import org.msvcompraentradasol.dtos.SolicitudResponseDTO;
import org.msvcompraentradasol.entities.Compra;
import org.msvcompraentradasol.entities.Entrada;
import org.msvcompraentradasol.entities.Solicitud;
import org.msvcompraentradasol.enums.EstadoEntrada;
import org.msvcompraentradasol.enums.EstadoSolicitud;
import org.msvcompraentradasol.enums.EstadoCompra;
import org.msvcompraentradasol.enums.Estadoqr;
import org.msvcompraentradasol.feigns.EventoClientFeign;
import org.msvcompraentradasol.feigns.NotificacionCompraClient;
import org.msvcompraentradasol.feigns.ReservaClientFeign;
import org.msvcompraentradasol.repositories.EntradaRepository;
import org.msvcompraentradasol.repositories.SolicitudRepository;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SolicitudService {

    private static final ZoneId ZONA_EVENTOS = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Set<String> ESTADOS_EVENTO_SIN_DEVOLUCION = Set.of("CANCELADO", "FINALIZADO");

    private final SolicitudRepository solicitudRepository;
    private final EntradaRepository entradaRepository;
    private final EventoClientFeign eventoClient;
    private final ReservaClientFeign reservaClient;
    private final PagoSimuladoService pagoSimuladoService;
    private final ApplicationEventPublisher eventos;

    public SolicitudService(
            SolicitudRepository solicitudRepository,
            EntradaRepository entradaRepository,
            EventoClientFeign eventoClient,
            ReservaClientFeign reservaClient,
            PagoSimuladoService pagoSimuladoService,
            ApplicationEventPublisher eventos
    ) {
        this.solicitudRepository = solicitudRepository;
        this.entradaRepository = entradaRepository;
        this.eventoClient = eventoClient;
        this.reservaClient = reservaClient;
        this.pagoSimuladoService = pagoSimuladoService;
        this.eventos = eventos;
    }

    @Transactional
    public SolicitudResponseDTO crearSolicitud(SolicitudRequestDTO dto, Long idCliente) {
        Entrada entrada = entradaRepository.findByIdParaActualizar(dto.getIdEntrada())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrada no encontrada"));

        validarCompraYTitular(entrada, idCliente);
        if (solicitudRepository.existsByEntrada_IdAndEstadoSolicitud(
                dto.getIdEntrada(),
                EstadoSolicitud.PENDIENTE
        ) || solicitudRepository.existsByEntrada_IdAndEstadoSolicitud(
                dto.getIdEntrada(),
                EstadoSolicitud.ACEPTADA
        )) {
            throw estadoInvalido("La entrada ya tiene una solicitud pendiente o aceptada");
        }
        if (entrada.getEstadoEntrada() != EstadoEntrada.PAGA) {
            throw estadoInvalido("El estado de la entrada no permite solicitar una devolución");
        }
        if (entrada.getIdEvento() == null) {
            throw estadoInvalido("La entrada no tiene un evento asociado");
        }

        EventoFechaResponseDTO evento = obtenerEvento(entrada.getIdEvento());
        validarEventoParaDevolucion(evento);

        Solicitud solicitud = new Solicitud();
        solicitud.setEntrada(entrada);
        solicitud.setIdCliente(idCliente);
        solicitud.setFechaSolicitud(Instant.now());
        solicitud.setDescripcion(dto.getDescripcion());
        solicitud.setEstadoSolicitud(EstadoSolicitud.PENDIENTE);

        entrada.setEstadoEntrada(EstadoEntrada.SOLICITUD_DEVOLUCION);
        entradaRepository.save(entrada);

        return SolicitudResponseDTO.from(solicitudRepository.save(solicitud));
    }

    @Transactional(readOnly = true)
    public SolicitudResponseDTO buscarPorId(Long idSolicitud) {
        return solicitudRepository.findById(idSolicitud)
                .map(SolicitudResponseDTO::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Solicitud no encontrada"
                ));
    }

    @Transactional(readOnly = true)
    public List<SolicitudResponseDTO> listarPorEntrada(Long idEntrada) {
        return solicitudRepository.findAllByEntrada_IdOrderByIdAsc(idEntrada).stream()
                .map(SolicitudResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SolicitudResponseDTO> listarPendientes() {
        return solicitudRepository.findAllByEstadoSolicitudOrderByIdAsc(EstadoSolicitud.PENDIENTE)
                .stream()
                .map(SolicitudResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SolicitudResponseDTO> listarPorCliente(Long idCliente) {
        return solicitudRepository.findAllByIdClienteOrderByIdAsc(idCliente).stream()
                .map(SolicitudResponseDTO::from)
                .toList();
    }

    @Transactional
    public SolicitudResponseDTO resolverSolicitud(
            Long idSolicitud,
            Long idAdministrador,
            EstadoSolicitud estado
    ) {
        if (estado != EstadoSolicitud.ACEPTADA && estado != EstadoSolicitud.RECHAZADA) {
            throw new IllegalArgumentException("Estado de resolución inválido");
        }

        Solicitud solicitud = solicitudRepository.findByIdParaResolver(idSolicitud)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Solicitud no encontrada"
                ));

        if (solicitud.getEntrada() == null) throw estadoInvalido("La solicitud no tiene una entrada asociada");
        Entrada entrada = entradaRepository.findByIdParaActualizar(solicitud.getEntrada().getId())
                .orElseThrow(() -> estadoInvalido("La solicitud no tiene una entrada asociada"));
        validarCompraYTitular(entrada, solicitud.getIdCliente());
        if (solicitud.getEstadoSolicitud() == estado) return SolicitudResponseDTO.from(solicitud);
        if (solicitud.getEstadoSolicitud() != EstadoSolicitud.PENDIENTE) {
            throw estadoInvalido("La solicitud ya fue resuelta con otro resultado");
        }
        if (entrada.getEstadoEntrada() != EstadoEntrada.SOLICITUD_DEVOLUCION) {
            throw estadoInvalido("La entrada no tiene una devolución pendiente");
        }

        solicitud.setIdAdministrador(idAdministrador);
        solicitud.setFechaResolucion(Instant.now());
        solicitud.setEstadoSolicitud(estado);

        if (estado == EstadoSolicitud.RECHAZADA) {
            entrada.setEstadoEntrada(EstadoEntrada.DEVOLUCION_RECHAZADA);
            entradaRepository.save(entrada);
            return guardarResolucion(solicitud, entrada);
        }

        Compra compra = entrada.getCompra();
        if (compra == null || compra.getIdReserva() == null) {
            throw estadoInvalido("La compra no tiene una reserva asociada");
        }

        List<Long> idAsientos = entrada.getIdAsiento() == null
                ? List.of()
                : List.of(entrada.getIdAsiento());

        ReservaResponseDTO liberacion;
        try {
            liberacion = reservaClient.devolver(
                    compra.getIdReserva(),
                    new DevolucionRequestDTO(
                            "devolucion-solicitud-" + solicitud.getId(),
                            1,
                            solicitud.getFechaSolicitud(),
                            idAsientos
                    )
            );
        } catch (FeignException.Conflict exception) {
            throw new IllegalStateException(
                    "No se pudo liberar el lugar de la entrada. Revisá el estado de la reserva antes de reintentar.",
                    exception
            );
        }
        if (!Set.of("CONFIRMADA", "DEVUELTA").contains(liberacion.estado())) {
            throw new IllegalStateException("SectorAsiento no liberó el lugar de la entrada");
        }

        PagoSimuladoService.ResultadoReintegro reintegro = pagoSimuladoService.reintegrar(
                compra.getReferenciaPago(),
                entrada.getPrecio(),
                "reintegro-solicitud-" + solicitud.getId()
        );
        if (!reintegro.aprobado()) {
            throw new IllegalStateException("La pasarela simulada rechazó el reintegro");
        }

        solicitud.setReferenciaReintegro(reintegro.referencia());
        solicitud.setEstadoReintegro(reintegro.estado());
        entrada.setEstadoEntrada(EstadoEntrada.DEVOLUCION_ACEPTADA);
        entradaRepository.save(entrada);

        return guardarResolucion(solicitud, entrada);
    }

    private SolicitudResponseDTO guardarResolucion(Solicitud solicitud, Entrada entrada) {
        SolicitudResponseDTO respuesta = SolicitudResponseDTO.from(solicitudRepository.save(solicitud));
        boolean aceptada = solicitud.getEstadoSolicitud() == EstadoSolicitud.ACEPTADA;
        eventos.publishEvent(new NotificacionCompraClient.Suceso(
                "devolucion-resuelta-" + respuesta.id(), aceptada ? "DEVOLUCION_APROBADA" : "DEVOLUCION_RECHAZADA",
                entrada.getIdEvento(), entrada.getCompra().getId(), entrada.getIdCliente(), null, 1L,
                aceptada ? entrada.getPrecio() : null, "Entrada #" + entrada.getId() + "."));
        return respuesta;
    }

    private void validarCompraYTitular(Entrada entrada, Long idCliente) {
        Compra compra = entrada.getCompra();
        if (!Objects.equals(entrada.getIdCliente(), idCliente)
                || (compra != null && !Objects.equals(compra.getIdCliente(), idCliente))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La entrada o compra no pertenece al solicitante");
        }
        if (compra == null || compra.getEstadoCompra() != EstadoCompra.APROBADA) {
            throw estadoInvalido("La entrada requiere una compra aprobada");
        }
        if (entrada.getEstadoqr() != Estadoqr.NO_ESCANEADO) {
            throw estadoInvalido("La entrada ya fue utilizada o su QR no está disponible para devolución");
        }
    }

    private ResponseStatusException estadoInvalido(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }

    private EventoFechaResponseDTO obtenerEvento(Long idEvento) {
        try {
            EventoFechaResponseDTO evento = eventoClient.obtenerEvento(idEvento);
            if (evento == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");
            }
            return evento;
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");
        }
    }

    private void validarEventoParaDevolucion(EventoFechaResponseDTO evento) {
        if (evento.estado() != null && ESTADOS_EVENTO_SIN_DEVOLUCION.contains(evento.estado())) {
            throw estadoInvalido("El evento no admite solicitudes de devolución");
        }
        if (evento.fechaHora() == null) {
            throw estadoInvalido("El evento no tiene una fecha configurada");
        }
        if (evento.fechaHora().isBefore(LocalDateTime.now(ZONA_EVENTOS).plusHours(72))) {
            throw estadoInvalido(
                    "La devolución debe solicitarse al menos 72 horas antes del evento"
            );
        }
    }
}
