package org.msvcompraentradasol.services;

import feign.FeignException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.msvcompraentradasol.dtos.CancelacionEventoResponseDTO;
import org.msvcompraentradasol.dtos.CompraRequestDTO;
import org.msvcompraentradasol.dtos.CompraResponseDTO;
import org.msvcompraentradasol.dtos.EventoFechaResponseDTO;
import org.msvcompraentradasol.dtos.ReservaRequestDTO;
import org.msvcompraentradasol.dtos.ReservaResponseDTO;
import org.msvcompraentradasol.entities.Compra;
import org.msvcompraentradasol.entities.Entrada;
import org.msvcompraentradasol.entities.Solicitud;
import org.msvcompraentradasol.enums.EstadoCompra;
import org.msvcompraentradasol.enums.EstadoEntrada;
import org.msvcompraentradasol.enums.EstadoReintegro;
import org.msvcompraentradasol.enums.EstadoSolicitud;
import org.msvcompraentradasol.feigns.EventoClientFeign;
import org.msvcompraentradasol.feigns.ReservaClientFeign;
import org.msvcompraentradasol.repositories.CompraRepository;
import org.msvcompraentradasol.repositories.EntradaRepository;
import org.msvcompraentradasol.repositories.SolicitudRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompraService {

    private static final ZoneId ZONA_EVENTOS = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Set<String> ESTADOS_EVENTO_NO_VENDIBLES =
            Set.of("CANCELADO", "SUSPENDIDO", "FINALIZADO", "AGOTADO");
    private static final int CANTIDAD_MAXIMA = 1000;

    private final CompraRepository compraRepository;
    private final EntradaRepository entradaRepository;
    private final SolicitudRepository solicitudRepository;
    private final EntradaService entradaService;
    private final ReservaClientFeign reservaClient;
    private final EventoClientFeign eventoClient;
    private final PagoSimuladoService pagoSimuladoService;

    public CompraService(
            CompraRepository compraRepository,
            EntradaRepository entradaRepository,
            SolicitudRepository solicitudRepository,
            EntradaService entradaService,
            ReservaClientFeign reservaClient,
            EventoClientFeign eventoClient,
            PagoSimuladoService pagoSimuladoService
    ) {
        this.compraRepository = compraRepository;
        this.entradaRepository = entradaRepository;
        this.solicitudRepository = solicitudRepository;
        this.entradaService = entradaService;
        this.reservaClient = reservaClient;
        this.eventoClient = eventoClient;
        this.pagoSimuladoService = pagoSimuladoService;
    }

    public CompraResponseDTO crearCompra(CompraRequestDTO dto) {
        List<Long> idsAsientos = dto.getIdsAsientos() == null
                ? List.of()
                : List.copyOf(dto.getIdsAsientos());
        int cantidad = idsAsientos.isEmpty()
                ? dto.getCantidadEntradas() == null ? 0 : dto.getCantidadEntradas()
                : idsAsientos.size();

        if (cantidad <= 0) {
            throw new IllegalArgumentException("Debe indicar una cantidad positiva o asientos seleccionados");
        }
        if (cantidad > CANTIDAD_MAXIMA) {
            throw new IllegalArgumentException("La cantidad de entradas no puede superar " + CANTIDAD_MAXIMA);
        }

        var compraExistente = compraRepository.findByClaveIdempotencia(dto.getClaveIdempotencia());
        if (compraExistente.isPresent()) {
            Compra existente = compraExistente.get();
            if (!coincideConPedido(existente, dto, idsAsientos, cantidad)) {
                throw new IllegalStateException("La clave de idempotencia ya corresponde a otra compra");
            }
            return CompraResponseDTO.from(existente);
        }

        validarEventoVendible(dto.getIdEvento());

        ReservaResponseDTO reserva = reservaClient.reservar(new ReservaRequestDTO(
                dto.getClaveIdempotencia(),
                dto.getIdCliente(),
                dto.getIdEvento(),
                dto.getIdSector(),
                cantidad,
                idsAsientos
        ));

        if (reserva.id() == null || reserva.precioUnitario() == null
                || !"ACTIVA".equals(reserva.estado())) {
            throw new IllegalStateException("SectorAsiento no devolvió una reserva activa con precio");
        }

        List<Long> asientosReservados = reserva.idAsientos() == null || reserva.idAsientos().isEmpty()
                ? idsAsientos
                : List.copyOf(reserva.idAsientos());

        Compra compra = new Compra();
        compra.setIdCliente(dto.getIdCliente());
        compra.setIdEvento(dto.getIdEvento());
        compra.setMedioDePago(dto.getMedioDePago());
        compra.setIdSector(dto.getIdSector());
        compra.setIdsAsientos(asientosReservados);
        compra.setCantidadEntradas((long) cantidad);
        compra.setClaveIdempotencia(dto.getClaveIdempotencia());
        compra.setIdReserva(reserva.id());
        compra.setPrecioUnitario(reserva.precioUnitario());
        compra.setImporteTotal(reserva.precioUnitario().multiply(BigDecimal.valueOf(cantidad)));
        compra.setDescripcion("Compra pendiente de pago");
        compra.setEstadoCompra(EstadoCompra.PENDIENTE);

        try {
            return CompraResponseDTO.from(compraRepository.saveAndFlush(compra));
        } catch (DataIntegrityViolationException exception) {
            return compraRepository.findByClaveIdempotencia(dto.getClaveIdempotencia())
                    .filter(existente -> coincideConPedido(existente, dto, idsAsientos, cantidad))
                    .map(CompraResponseDTO::from)
                    .orElseThrow(() -> new IllegalStateException(
                            "No se pudo registrar la compra por un conflicto de datos"
                    ));
        }
    }

    @Transactional(readOnly = true)
    public CompraResponseDTO buscarPorId(Long idCompra) {
        return compraRepository.buscarConAsientos(idCompra)
                .map(CompraResponseDTO::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra no encontrada"));
    }

    @Transactional(readOnly = true)
    public List<CompraResponseDTO> listarPorCliente(Long idCliente) {
        return compraRepository.findAllByIdClienteOrderByIdDesc(idCliente).stream()
                .map(CompraResponseDTO::from)
                .toList();
    }

    @Transactional
    public CompraResponseDTO procesarPago(Long idCompra, boolean aprobado) {
        Compra compra = compraRepository.buscarPorIdParaActualizar(idCompra)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra no encontrada"));

        if (compra.getEstadoCompra() != EstadoCompra.PENDIENTE) {
            if (aprobado && (compra.getEstadoCompra() == EstadoCompra.APROBADA
                    || compra.getEstadoCompra() == EstadoCompra.EXPIRADA)) {
                return CompraResponseDTO.from(compra);
            }
            if (!aprobado && compra.getEstadoCompra() == EstadoCompra.RECHAZADA) {
                return CompraResponseDTO.from(compra);
            }
            throw new IllegalStateException("La compra ya fue procesada con otro resultado");
        }

        validarEventoVendible(compra.getIdEvento());

        PagoSimuladoService.ResultadoPago resultadoPago = pagoSimuladoService.procesar(
                compra.getImporteTotal(),
                compra.getClaveIdempotencia(),
                aprobado
        );
        compra.setReferenciaPago(resultadoPago.referencia());
        compra.setDescripcion(resultadoPago.detalle());

        if (!resultadoPago.aprobado()) {
            liberarReserva(compra, Set.of("LIBERADA", "VENCIDA"));
            compra.setEstadoCompra(EstadoCompra.RECHAZADA);
            return CompraResponseDTO.from(compraRepository.save(compra));
        }

        ReservaResponseDTO confirmacion = reservaClient.confirmar(compra.getIdReserva());
        if ("VENCIDA".equals(confirmacion.estado())) {
            PagoSimuladoService.ResultadoReintegro reintegro = pagoSimuladoService.reintegrar(
                    compra.getReferenciaPago(),
                    compra.getImporteTotal(),
                    "reserva-vencida-" + compra.getClaveIdempotencia()
            );
            if (!reintegro.aprobado()) {
                throw new IllegalStateException("No se pudo reintegrar el pago de la reserva vencida");
            }
            compra.setReferenciaReintegro(reintegro.referencia());
            compra.setEstadoReintegro(reintegro.estado());
            compra.setEstadoCompra(EstadoCompra.EXPIRADA);
            compra.setDescripcion("La reserva venció y el pago simulado fue reintegrado");
            return CompraResponseDTO.from(compraRepository.save(compra));
        }
        if (!"CONFIRMADA".equals(confirmacion.estado())) {
            throw new IllegalStateException("La reserva no pudo confirmarse tras aprobar el pago");
        }

        compra.setEstadoCompra(EstadoCompra.APROBADA);
        List<Long> idsAsientos = compra.getIdsAsientos() == null
                ? List.of()
                : List.copyOf(compra.getIdsAsientos());

        if (!idsAsientos.isEmpty()) {
            for (Long idAsiento : idsAsientos) {
                entradaService.crearEntrada(
                        compra.getIdCliente(),
                        compra.getIdEvento(),
                        compra.getIdSector(),
                        idAsiento,
                        compra.getPrecioUnitario(),
                        compra
                );
            }
        } else {
            long cantidad = compra.getCantidadEntradas() == null ? 0 : compra.getCantidadEntradas();
            for (long i = 0; i < cantidad; i++) {
                entradaService.crearEntrada(
                        compra.getIdCliente(),
                        compra.getIdEvento(),
                        compra.getIdSector(),
                        null,
                        compra.getPrecioUnitario(),
                        compra
                );
            }
        }

        return CompraResponseDTO.from(compraRepository.save(compra));
    }

    @Transactional
    public CancelacionEventoResponseDTO cancelarEvento(Long idEvento) {
        List<Compra> compras = compraRepository.buscarPorEventoParaActualizar(
                idEvento,
                List.of(EstadoCompra.APROBADA, EstadoCompra.PENDIENTE)
        );
        int comprasCanceladas = 0;
        int entradasReintegradas = 0;
        BigDecimal importeReintegrado = BigDecimal.ZERO;

        for (Compra compra : compras) {
            if (compra.getEstadoCompra() == EstadoCompra.PENDIENTE) {
                liberarReserva(compra, Set.of("LIBERADA", "VENCIDA"));
                compra.setEstadoCompra(EstadoCompra.CANCELADA);
                compra.setDescripcion("Compra cancelada antes del pago por cancelación del evento");
                compraRepository.save(compra);
                comprasCanceladas++;
                continue;
            }

            List<Entrada> entradas = entradaRepository.findAllByCompra_Id(compra.getId());
            List<Entrada> reintegrables = entradas.stream()
                    .filter(entrada -> entrada.getEstadoEntrada() != EstadoEntrada.DEVOLUCION_ACEPTADA)
                    .filter(entrada -> entrada.getEstadoEntrada() != EstadoEntrada.EVENTO_CANCELADO)
                    .toList();

            BigDecimal importe = reintegrables.stream()
                    .map(Entrada::getPrecio)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String referenciaReintegro = compra.getReferenciaReintegro();
            if (importe.signum() > 0) {
                PagoSimuladoService.ResultadoReintegro reintegro = pagoSimuladoService.reintegrar(
                        compra.getReferenciaPago(),
                        importe,
                        "cancelacion-evento-" + idEvento + "-compra-" + compra.getId()
                );
                if (!reintegro.aprobado()) {
                    throw new IllegalStateException("No se pudo reintegrar la compra " + compra.getId());
                }
                compra.setReferenciaReintegro(reintegro.referencia());
                compra.setEstadoReintegro(reintegro.estado());
                referenciaReintegro = reintegro.referencia();
                importeReintegrado = importeReintegrado.add(importe);
            }

            reintegrables.forEach(entrada -> entrada.setEstadoEntrada(EstadoEntrada.EVENTO_CANCELADO));
            entradaRepository.saveAll(reintegrables);
            entradasReintegradas += reintegrables.size();
            cancelarSolicitudesPendientes(reintegrables, referenciaReintegro);

            compra.setEstadoCompra(EstadoCompra.CANCELADA);
            compra.setDescripcion("Compra cancelada por cancelación del evento");
            compraRepository.save(compra);
            comprasCanceladas++;
        }

        boolean inventarioCancelado = cancelarInventario(idEvento);
        return new CancelacionEventoResponseDTO(
                idEvento,
                comprasCanceladas,
                entradasReintegradas,
                importeReintegrado,
                inventarioCancelado
        );
    }

    private void cancelarSolicitudesPendientes(List<Entrada> entradas, String referenciaReintegro) {
        for (Entrada entrada : entradas) {
            List<Solicitud> pendientes = solicitudRepository.findAllByEntrada_IdAndEstadoSolicitud(
                    entrada.getId(),
                    EstadoSolicitud.PENDIENTE
            );
            for (Solicitud solicitud : pendientes) {
                solicitud.setEstadoSolicitud(EstadoSolicitud.CANCELADA);
                solicitud.setFechaResolucion(Instant.now());
                solicitud.setReferenciaReintegro(referenciaReintegro);
                solicitud.setEstadoReintegro(
                        referenciaReintegro == null ? null : EstadoReintegro.COMPLETADO
                );
                solicitud.setDescripcion(
                        solicitud.getDescripcion() + " (cancelada por cancelación del evento)"
                );
                solicitudRepository.save(solicitud);
            }
        }
    }

    private void liberarReserva(Compra compra, Set<String> estadosEsperados) {
        ReservaResponseDTO liberacion = reservaClient.liberar(compra.getIdReserva());
        if (!estadosEsperados.contains(liberacion.estado())) {
            throw new IllegalStateException(
                    "La reserva no pudo liberarse; estado actual: " + liberacion.estado()
            );
        }
    }

    private boolean cancelarInventario(Long idEvento) {
        try {
            reservaClient.cancelarInventario(idEvento);
            return true;
        } catch (FeignException exception) {
            if (exception.status() == HttpStatus.NOT_FOUND.value()) {
                return false;
            }
            throw exception;
        }
    }

    private EventoFechaResponseDTO validarEventoVendible(Long idEvento) {
        EventoFechaResponseDTO evento;
        try {
            evento = eventoClient.obtenerEvento(idEvento);
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");
        }
        if (evento == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");
        }
        if (!"PUBLICADO".equals(evento.estadoPublicacion())) {
            throw new IllegalStateException("El evento no está publicado");
        }
        if (evento.estado() != null && ESTADOS_EVENTO_NO_VENDIBLES.contains(evento.estado())) {
            throw new IllegalStateException("El evento no admite nuevas compras");
        }
        if (evento.fechaHora() == null
                || !evento.fechaHora().isAfter(LocalDateTime.now(ZONA_EVENTOS))) {
            throw new IllegalStateException("El evento ya comenzó o no tiene fecha configurada");
        }
        return evento;
    }

    private boolean coincideConPedido(
            Compra compra,
            CompraRequestDTO dto,
            List<Long> idsAsientos,
            int cantidad
    ) {
        return Objects.equals(compra.getIdCliente(), dto.getIdCliente())
                && Objects.equals(compra.getIdEvento(), dto.getIdEvento())
                && Objects.equals(compra.getIdSector(), dto.getIdSector())
                && Objects.equals(compra.getCantidadEntradas(), (long) cantidad)
                && Objects.equals(compra.getMedioDePago(), dto.getMedioDePago())
                && new HashSet<>(compra.getIdsAsientos()).equals(new HashSet<>(idsAsientos));
    }
}
