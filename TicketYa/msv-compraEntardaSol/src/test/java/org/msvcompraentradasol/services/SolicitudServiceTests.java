package org.msvcompraentradasol.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.msvcompraentradasol.support.TestFixtures.eventoEn;
import static org.msvcompraentradasol.support.TestFixtures.eventoFuturo;
import static org.msvcompraentradasol.support.TestFixtures.reserva;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.msvcompraentradasol.dtos.DevolucionRequestDTO;
import org.msvcompraentradasol.dtos.SolicitudRequestDTO;
import org.msvcompraentradasol.dtos.SolicitudResponseDTO;
import org.msvcompraentradasol.entities.Compra;
import org.msvcompraentradasol.entities.Entrada;
import org.msvcompraentradasol.entities.Solicitud;
import org.msvcompraentradasol.enums.EstadoCompra;
import org.msvcompraentradasol.enums.EstadoEntrada;
import org.msvcompraentradasol.enums.EstadoSolicitud;
import org.msvcompraentradasol.enums.Estadoqr;
import org.msvcompraentradasol.feigns.EventoClientFeign;
import org.msvcompraentradasol.feigns.ReservaClientFeign;
import org.msvcompraentradasol.repositories.CompraRepository;
import org.msvcompraentradasol.repositories.EntradaRepository;
import org.msvcompraentradasol.repositories.SolicitudRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class SolicitudServiceTests {

    private static final Long ID_EVENTO = 1L;
    private static final Long ID_CLIENTE = 2L;
    private static final Long ID_RESERVA = 55L;

    @Autowired
    private SolicitudService solicitudService;

    @Autowired
    private SolicitudRepository solicitudRepository;

    @Autowired
    private EntradaRepository entradaRepository;

    @Autowired
    private CompraRepository compraRepository;

    @MockitoBean
    private EventoClientFeign eventoClient;

    @MockitoBean
    private ReservaClientFeign reservaClient;

    private Entrada entrada;

    @BeforeEach
    void prepararEntrada() {
        solicitudRepository.deleteAll();
        entradaRepository.deleteAll();
        compraRepository.deleteAll();

        Compra compra = compraRepository.save(compraDePrueba());
        entrada = entradaRepository.save(entradaDePrueba(compra));
    }

    @Test
    void crearSolicitudValidaQuedaPendiente() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(eventoFuturo(ID_EVENTO));

        SolicitudResponseDTO respuesta = solicitudService.crearSolicitud(
                solicitud(entrada.getId(), ID_CLIENTE)
        );

        assertThat(respuesta.id()).isNotNull();
        assertThat(respuesta.estadoSolicitud()).isEqualTo("PENDIENTE");
        assertThat(respuesta.idEntrada()).isEqualTo(entrada.getId());
        assertThat(respuesta.idCompra()).isEqualTo(entrada.getCompra().getId());
        assertThat(respuesta.fechaSolicitud()).isNotNull();
        assertThat(entradaRepository.findById(entrada.getId()).orElseThrow().getEstadoEntrada())
                .isEqualTo(EstadoEntrada.SOLICITUD_DEVOLUCION);
    }

    @Test
    void crearSolicitudDeOtroClienteDevuelve403() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(eventoFuturo(ID_EVENTO));

        assertThatThrownBy(() -> solicitudService.crearSolicitud(
                solicitud(entrada.getId(), 999L)
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode().value()
                ).isEqualTo(403));
    }

    @Test
    void crearSolicitudDeEntradaInexistenteDevuelve404() {
        assertThatThrownBy(() -> solicitudService.crearSolicitud(solicitud(999L, ID_CLIENTE)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode().value()
                ).isEqualTo(404));
    }

    @Test
    void crearSolicitudConMenosDe72HorasFalla() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(
                eventoEn(ID_EVENTO, LocalDateTime.now(java.time.ZoneId.of(
                        "America/Argentina/Buenos_Aires"
                )).plusHours(24))
        );

        assertThatThrownBy(() -> solicitudService.crearSolicitud(
                solicitud(entrada.getId(), ID_CLIENTE)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("72 horas");
    }

    @Test
    void crearSolicitudDeEventoCanceladoFalla() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(
                new org.msvcompraentradasol.dtos.EventoFechaResponseDTO(
                        ID_EVENTO,
                        LocalDateTime.now().plusDays(30),
                        "CANCELADO",
                        "PUBLICADO",
                        null
                )
        );

        assertThatThrownBy(() -> solicitudService.crearSolicitud(
                solicitud(entrada.getId(), ID_CLIENTE)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no admite");
    }

    @Test
    void crearSolicitudDuplicadaFalla() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(eventoFuturo(ID_EVENTO));
        solicitudService.crearSolicitud(solicitud(entrada.getId(), ID_CLIENTE));

        assertThatThrownBy(() -> solicitudService.crearSolicitud(
                solicitud(entrada.getId(), ID_CLIENTE)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nueva solicitud");
    }

    @Test
    void crearSolicitudDeEntradaNoDisponibleFalla() {
        entrada.setEstadoEntrada(EstadoEntrada.DEVOLUCION_ACEPTADA);
        entradaRepository.save(entrada);

        assertThatThrownBy(() -> solicitudService.crearSolicitud(
                solicitud(entrada.getId(), ID_CLIENTE)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no está disponible");
    }

    @Test
    void resolverAceptadaLiberaEntradaYReintegra() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(eventoFuturo(ID_EVENTO));
        SolicitudResponseDTO creada = solicitudService.crearSolicitud(
                solicitud(entrada.getId(), ID_CLIENTE)
        );
        when(reservaClient.devolver(eq(ID_RESERVA), any()))
                .thenReturn(reserva(ID_RESERVA, "1500.00", "DEVUELTA", List.of(11L)));

        SolicitudResponseDTO resuelta = solicitudService.resolverSolicitud(
                creada.id(), 9L, EstadoSolicitud.ACEPTADA
        );

        assertThat(resuelta.estadoSolicitud()).isEqualTo("ACEPTADA");
        assertThat(resuelta.estadoReintegro()).isEqualTo("COMPLETADO");
        assertThat(resuelta.referenciaReintegro()).startsWith("SIM-REF-");
        assertThat(resuelta.idAdministrador()).isEqualTo(9L);
        assertThat(resuelta.fechaResolucion()).isNotNull();
        assertThat(entradaRepository.findById(entrada.getId()).orElseThrow().getEstadoEntrada())
                .isEqualTo(EstadoEntrada.DEVOLUCION_ACEPTADA);

        ArgumentCaptor<DevolucionRequestDTO> captor =
                ArgumentCaptor.forClass(DevolucionRequestDTO.class);
        verify(reservaClient).devolver(eq(ID_RESERVA), captor.capture());
        assertThat(captor.getValue().cantidad()).isEqualTo(1);
        assertThat(captor.getValue().idAsientos()).containsExactly(11L);
        assertThat(captor.getValue().claveIdempotencia())
                .isEqualTo("devolucion-solicitud-" + creada.id());
    }

    @Test
    void resolverAceptadaUsaLaFechaDeLaSolicitudNoLaActual() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(eventoFuturo(ID_EVENTO));
        SolicitudResponseDTO creada = solicitudService.crearSolicitud(
                solicitud(entrada.getId(), ID_CLIENTE)
        );

        Instant fechaSolicitud = Instant.parse("2026-01-01T10:00:00.123456Z");
        Solicitud solicitudGuardada = solicitudRepository.findById(creada.id()).orElseThrow();
        solicitudGuardada.setFechaSolicitud(fechaSolicitud);
        solicitudRepository.saveAndFlush(solicitudGuardada);

        when(reservaClient.devolver(eq(ID_RESERVA), any()))
                .thenReturn(reserva(ID_RESERVA, "1500.00", "DEVUELTA", List.of(11L)));

        solicitudService.resolverSolicitud(creada.id(), 9L, EstadoSolicitud.ACEPTADA);

        ArgumentCaptor<DevolucionRequestDTO> captor =
                ArgumentCaptor.forClass(DevolucionRequestDTO.class);
        verify(reservaClient).devolver(eq(ID_RESERVA), captor.capture());
        assertThat(captor.getValue().solicitadaEn()).isEqualTo(fechaSolicitud);
    }

    @Test
    void resolverRechazadaDevuelveEntradaAlCliente() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(eventoFuturo(ID_EVENTO));
        SolicitudResponseDTO creada = solicitudService.crearSolicitud(
                solicitud(entrada.getId(), ID_CLIENTE)
        );

        SolicitudResponseDTO resuelta = solicitudService.resolverSolicitud(
                creada.id(), 9L, EstadoSolicitud.RECHAZADA
        );

        assertThat(resuelta.estadoSolicitud()).isEqualTo("RECHAZADA");
        assertThat(resuelta.estadoReintegro()).isNull();
        assertThat(entradaRepository.findById(entrada.getId()).orElseThrow().getEstadoEntrada())
                .isEqualTo(EstadoEntrada.DEVOLUCION_RECHAZADA);
        verify(reservaClient, never()).devolver(any(), any());
    }

    @Test
    void resolverMismoEstadoEsIdempotente() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(eventoFuturo(ID_EVENTO));
        SolicitudResponseDTO creada = solicitudService.crearSolicitud(
                solicitud(entrada.getId(), ID_CLIENTE)
        );
        when(reservaClient.devolver(eq(ID_RESERVA), any()))
                .thenReturn(reserva(ID_RESERVA, "1500.00", "DEVUELTA", List.of(11L)));

        SolicitudResponseDTO primera = solicitudService.resolverSolicitud(
                creada.id(), 9L, EstadoSolicitud.ACEPTADA
        );
        SolicitudResponseDTO segunda = solicitudService.resolverSolicitud(
                creada.id(), 9L, EstadoSolicitud.ACEPTADA
        );

        assertThat(segunda.referenciaReintegro()).isEqualTo(primera.referenciaReintegro());
        verify(reservaClient, times(1)).devolver(any(), any());
    }

    @Test
    void resolverConOtroEstadoFalla() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(eventoFuturo(ID_EVENTO));
        SolicitudResponseDTO creada = solicitudService.crearSolicitud(
                solicitud(entrada.getId(), ID_CLIENTE)
        );
        solicitudService.resolverSolicitud(creada.id(), 9L, EstadoSolicitud.RECHAZADA);

        assertThatThrownBy(() -> solicitudService.resolverSolicitud(
                creada.id(), 9L, EstadoSolicitud.ACEPTADA
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("otro resultado");
    }

    @Test
    void resolverConEstadoInvalidoFalla() {
        assertThatThrownBy(() -> solicitudService.resolverSolicitud(
                1L, 9L, EstadoSolicitud.PENDIENTE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inválido");
    }

    @Test
    void resolverSolicitudInexistenteDevuelve404() {
        assertThatThrownBy(() -> solicitudService.resolverSolicitud(
                999L, 9L, EstadoSolicitud.ACEPTADA
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode().value()
                ).isEqualTo(404));
    }

    @Test
    void resolverSolicitudDeEventoCanceladoFalla() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(eventoFuturo(ID_EVENTO));
        SolicitudResponseDTO creada = solicitudService.crearSolicitud(
                solicitud(entrada.getId(), ID_CLIENTE)
        );
        entrada.setEstadoEntrada(EstadoEntrada.EVENTO_CANCELADO);
        entradaRepository.save(entrada);

        assertThatThrownBy(() -> solicitudService.resolverSolicitud(
                creada.id(), 9L, EstadoSolicitud.ACEPTADA
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evento fue cancelado");
    }

    @Test
    void consultasDeSolicitudes() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(eventoFuturo(ID_EVENTO));
        SolicitudResponseDTO creada = solicitudService.crearSolicitud(
                solicitud(entrada.getId(), ID_CLIENTE)
        );

        assertThat(solicitudService.buscarPorId(creada.id()).id()).isEqualTo(creada.id());
        assertThat(solicitudService.listarPorEntrada(entrada.getId())).hasSize(1);
        assertThat(solicitudService.listarPendientes()).hasSize(1);
        assertThat(solicitudService.listarPorCliente(ID_CLIENTE)).hasSize(1);
    }

    private static SolicitudRequestDTO solicitud(Long idEntrada, Long idCliente) {
        SolicitudRequestDTO dto = new SolicitudRequestDTO();
        dto.setIdEntrada(idEntrada);
        dto.setIdCliente(idCliente);
        dto.setDescripcion("No puedo asistir al evento");
        return dto;
    }

    private static Compra compraDePrueba() {
        Compra compra = new Compra();
        compra.setIdCliente(ID_CLIENTE);
        compra.setMedioDePago("TARJETA");
        compra.setEstadoCompra(EstadoCompra.APROBADA);
        compra.setIdEvento(ID_EVENTO);
        compra.setDescripcion("Compra de prueba");
        compra.setCantidadEntradas(1L);
        compra.setIdSector(3L);
        compra.setClaveIdempotencia("compra-solicitud");
        compra.setIdReserva(ID_RESERVA);
        compra.setPrecioUnitario(new BigDecimal("1500.00"));
        compra.setImporteTotal(new BigDecimal("1500.00"));
        compra.setReferenciaPago("SIM-PAGO");
        return compra;
    }

    private static Entrada entradaDePrueba(Compra compra) {
        Entrada nueva = new Entrada();
        nueva.setQr(java.util.UUID.randomUUID().toString());
        nueva.setEstadoqr(Estadoqr.NO_ESCANEADO);
        nueva.setIdCliente(ID_CLIENTE);
        nueva.setIdEvento(ID_EVENTO);
        nueva.setIdSector(3L);
        nueva.setIdAsiento(11L);
        nueva.setPrecio(new BigDecimal("1500.00"));
        nueva.setEstadoEntrada(EstadoEntrada.PAGA);
        nueva.setCompra(compra);
        return nueva;
    }
}
