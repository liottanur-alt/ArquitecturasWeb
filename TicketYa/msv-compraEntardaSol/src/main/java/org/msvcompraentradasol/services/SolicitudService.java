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
import org.msvcompraentradasol.feigns.EventoClientFeign;
import org.msvcompraentradasol.feigns.ReservaClientFeign;
import org.msvcompraentradasol.repositories.EntradaRepository;
import org.msvcompraentradasol.repositories.SolicitudRepository;
import org.springframework.http.HttpStatus;
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
    public SolicitudResponseDTO crearSolicitud(SolicitudRequestDTO dto) {
        Entrada entrada = entradaRepository.findById(dto.getIdEntrada())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entrada no encontrada"));

        if (!Objects.equals(entrada.getIdCliente(), dto.getIdCliente())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "La entrada no pertenece al cliente indicado"
            );
        }
        if (solicitudRepository.existsByEntrada_IdAndEstadoSolicitud(
                dto.getIdEntrada(),
                EstadoSolicitud.PENDIENTE
        ) || solicitudRepository.existsByEntrada_IdAndEstadoSolicitud(
                dto.getIdEntrada(),
                EstadoSolicitud.ACEPTADA
        )) {
            throw new IllegalStateException("No se puede realizar una nueva solicitud");
        }
        if (entrada.getEstadoEntrada() != EstadoEntrada.PAGA
                && entrada.getEstadoEntrada() != EstadoEntrada.DEVOLUCION_RECHAZADA) {
            throw new IllegalStateException("La entrada no está disponible para solicitar una devolución");
        }
        if (entrada.getIdEvento() == null) {
            throw new IllegalStateException("La entrada no tiene un evento asociado");
        }

        EventoFechaResponseDTO evento = obtenerEvento(entrada.getIdEvento());
        validarEventoParaDevolucion(evento);

        Solicitud solicitud = new Solicitud();
        solicitud.setEntrada(entrada);
        solicitud.setIdCliente(dto.getIdCliente());
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

        Solicitud solicitud = solicitudRepository.findById(idSolicitud)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Solicitud no encontrada"
                ));

        if (solicitud.getEstadoSolicitud() == estado) {
            return SolicitudResponseDTO.from(solicitud);
        }
        if (solicitud.getEstadoSolicitud() != EstadoSolicitud.PENDIENTE) {
            throw new IllegalStateException("La solicitud ya fue resuelta con otro resultado");
        }

        Entrada entrada = solicitud.getEntrada();
        if (entrada == null) {
            throw new IllegalStateException("La solicitud no tiene una entrada asociada");
        }
        if (entrada.getEstadoEntrada() == EstadoEntrada.EVENTO_CANCELADO) {
            throw new IllegalStateException("El evento fue cancelado y el reintegro ya fue procesado");
        }

        solicitud.setIdAdministrador(idAdministrador);
        solicitud.setFechaResolucion(Instant.now());
        solicitud.setEstadoSolicitud(estado);

        if (estado == EstadoSolicitud.RECHAZADA) {
            entrada.setEstadoEntrada(EstadoEntrada.DEVOLUCION_RECHAZADA);
            entradaRepository.save(entrada);
            return SolicitudResponseDTO.from(solicitudRepository.save(solicitud));
        }

        if (entrada.getEstadoEntrada() != EstadoEntrada.SOLICITUD_DEVOLUCION) {
            throw new IllegalStateException("La entrada no está en solicitud de devolución");
        }
        Compra compra = entrada.getCompra();
        if (compra == null || compra.getIdReserva() == null) {
            throw new IllegalStateException("La compra no tiene una reserva asociada");
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
            throw new IllegalStateException("SectorAsiento rechazó la devolución de la entrada");
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

        return SolicitudResponseDTO.from(solicitudRepository.save(solicitud));
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
            throw new IllegalStateException("El evento no admite solicitudes de devolución");
        }
        if (evento.fechaHora() == null) {
            throw new IllegalStateException("El evento no tiene una fecha configurada");
        }
        if (evento.fechaHora().isBefore(LocalDateTime.now(ZONA_EVENTOS).plusHours(72))) {
            throw new IllegalStateException(
                    "La devolución debe solicitarse al menos 72 horas antes del evento"
            );
        }
    }
}
