package org.msvcompraentradasol.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.msvcompraentradasol.support.TestFixtures.compraConAsientos;
import static org.msvcompraentradasol.support.TestFixtures.compraSinAsientos;
import static org.msvcompraentradasol.support.TestFixtures.eventoFuturo;
import static org.msvcompraentradasol.support.TestFixtures.reserva;
import static org.msvcompraentradasol.support.TestFixtures.reservaActiva;

import feign.FeignException;
import feign.Request;
import feign.Response;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.msvcompraentradasol.dtos.CancelacionEventoResponseDTO;
import org.msvcompraentradasol.dtos.CompraResponseDTO;
import org.msvcompraentradasol.entities.Compra;
import org.msvcompraentradasol.entities.Entrada;
import org.msvcompraentradasol.enums.EstadoCompra;
import org.msvcompraentradasol.enums.EstadoEntrada;
import org.msvcompraentradasol.enums.EstadoReintegro;
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
class CompraServiceTests {

    private static final Long ID_EVENTO = 1L;
    private static final Long ID_CLIENTE = 2L;
    private static final Long ID_RESERVA = 55L;

    @Autowired
    private CompraService compraService;

    @Autowired
    private CompraRepository compraRepository;

    @Autowired
    private EntradaRepository entradaRepository;

    @Autowired
    private SolicitudRepository solicitudRepository;

    @MockitoBean
    private ReservaClientFeign reservaClient;

    @MockitoBean
    private EventoClientFeign eventoClient;

    @BeforeEach
    void limpiarBase() {
        solicitudRepository.deleteAll();
        entradaRepository.deleteAll();
        compraRepository.deleteAll();
    }

    @Test
    void crearCompraConAsientosCalculaImporteYGuardaPendiente() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(eventoFuturo(ID_EVENTO));
        when(reservaClient.reservar(any()))
                .thenReturn(reservaActiva(ID_RESERVA, "1500.00", List.of(11L, 12L)));

        CompraResponseDTO compra = compraService.crearCompra(
                compraConAsientos("clave-1", ID_CLIENTE, ID_EVENTO, List.of(11L, 12L))
        );

        assertThat(compra.id()).isNotNull();
        assertThat(compra.estadoCompra()).isEqualTo("PENDIENTE");
        assertThat(compra.idReserva()).isEqualTo(ID_RESERVA);
        assertThat(compra.precioUnitario()).isEqualByComparingTo("1500.00");
        assertThat(compra.importeTotal()).isEqualByComparingTo("3000.00");
        assertThat(compra.cantidadEntradas()).isEqualTo(2L);
        assertThat(compra.idsAsientos()).containsExactly(11L, 12L);

        verify(reservaClient).reservar(org.mockito.ArgumentMatchers.argThat(
                solicitud -> solicitud.cantidad() == 2
                        && solicitud.idAsientos().equals(List.of(11L, 12L))
                        && solicitud.claveIdempotencia().equals("clave-1")
        ));
    }

    @Test
    void crearCompraSinAsientosUsaCantidadIndicada() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(eventoFuturo(ID_EVENTO));
        when(reservaClient.reservar(any()))
                .thenReturn(reservaActiva(ID_RESERVA, "900.00", List.of()));

        CompraResponseDTO compra = compraService.crearCompra(
                compraSinAsientos("clave-campo", ID_CLIENTE, ID_EVENTO, 3)
        );

        assertThat(compra.cantidadEntradas()).isEqualTo(3L);
        assertThat(compra.importeTotal()).isEqualByComparingTo("2700.00");
        assertThat(compra.idsAsientos()).isEmpty();
    }

    @Test
    void crearCompraEsIdempotenteConLaMismaClave() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(eventoFuturo(ID_EVENTO));
        when(reservaClient.reservar(any()))
                .thenReturn(reservaActiva(ID_RESERVA, "1500.00", List.of(11L)));

        CompraResponseDTO primera = compraService.crearCompra(
                compraConAsientos("clave-repetida", ID_CLIENTE, ID_EVENTO, List.of(11L))
        );
        CompraResponseDTO segunda = compraService.crearCompra(
                compraConAsientos("clave-repetida", ID_CLIENTE, ID_EVENTO, List.of(11L))
        );

        assertThat(segunda.id()).isEqualTo(primera.id());
        verify(reservaClient, times(1)).reservar(any());
        assertThat(compraRepository.count()).isEqualTo(1);
    }

    @Test
    void crearCompraConClaveReutilizadaParaOtroPedidoFalla() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(eventoFuturo(ID_EVENTO));
        when(reservaClient.reservar(any()))
                .thenReturn(reservaActiva(ID_RESERVA, "1500.00", List.of(11L)));

        compraService.crearCompra(
                compraConAsientos("clave-unica", ID_CLIENTE, ID_EVENTO, List.of(11L))
        );

        assertThatThrownBy(() -> compraService.crearCompra(
                compraConAsientos("clave-unica", ID_CLIENTE, ID_EVENTO, List.of(99L))
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idempotencia");
    }

    @Test
    void crearCompraDeEventoNoPublicadoFalla() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(
                new org.msvcompraentradasol.dtos.EventoFechaResponseDTO(
                        ID_EVENTO,
                        java.time.LocalDateTime.now().plusDays(10),
                        "ACTIVO",
                        "BORRADOR",
                        null
                )
        );

        assertThatThrownBy(() -> compraService.crearCompra(
                compraConAsientos("clave-borrador", ID_CLIENTE, ID_EVENTO, List.of(11L))
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publicado");

        verify(reservaClient, never()).reservar(any());
    }

    @Test
    void crearCompraDeEventoInexistenteDevuelve404() {
        when(eventoClient.obtenerEvento(ID_EVENTO))
                .thenThrow(feignError(404, "obtenerEvento"));

        assertThatThrownBy(() -> compraService.crearCompra(
                compraConAsientos("clave-inexistente", ID_CLIENTE, ID_EVENTO, List.of(11L))
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode().value()
                ).isEqualTo(404));
    }

    @Test
    void pagoAprobadoConfirmaReservaYCreaEntradas() {
        CompraResponseDTO creada = crearCompraPendiente();
        when(reservaClient.confirmar(ID_RESERVA))
                .thenReturn(reserva(ID_RESERVA, "1500.00", "CONFIRMADA", List.of(11L, 12L)));

        CompraResponseDTO pagada = compraService.procesarPago(creada.id(), true);

        assertThat(pagada.estadoCompra()).isEqualTo("APROBADA");
        assertThat(pagada.referenciaPago()).startsWith("SIM-");

        List<Entrada> entradas = entradaRepository.findAllByCompra_Id(creada.id());
        assertThat(entradas).hasSize(2);
        assertThat(entradas).allSatisfy(entrada -> {
            assertThat(entrada.getEstadoEntrada()).isEqualTo(EstadoEntrada.PAGA);
            assertThat(entrada.getQr()).isNotBlank();
        });
    }

    @Test
    void pagoAprobadoRepetidoNoDuplicaEntradas() {
        CompraResponseDTO creada = crearCompraPendiente();
        when(reservaClient.confirmar(ID_RESERVA))
                .thenReturn(reserva(ID_RESERVA, "1500.00", "CONFIRMADA", List.of(11L, 12L)));

        compraService.procesarPago(creada.id(), true);
        compraService.procesarPago(creada.id(), true);

        assertThat(entradaRepository.findAllByCompra_Id(creada.id())).hasSize(2);
        verify(reservaClient, times(1)).confirmar(ID_RESERVA);
    }

    @Test
    void pagoRechazadoLiberaReservaYNoCreaEntradas() {
        CompraResponseDTO creada = crearCompraPendiente();
        when(reservaClient.liberar(ID_RESERVA))
                .thenReturn(reserva(ID_RESERVA, "1500.00", "LIBERADA", List.of(11L, 12L)));

        CompraResponseDTO rechazada = compraService.procesarPago(creada.id(), false);

        assertThat(rechazada.estadoCompra()).isEqualTo("RECHAZADA");
        assertThat(entradaRepository.findAllByCompra_Id(creada.id())).isEmpty();
        verify(reservaClient).liberar(ID_RESERVA);
    }

    @Test
    void pagoAprobadoSobreReservaVencidaReintegraYExpira() {
        CompraResponseDTO creada = crearCompraPendiente();
        when(reservaClient.confirmar(ID_RESERVA))
                .thenReturn(reserva(ID_RESERVA, "1500.00", "VENCIDA", List.of(11L, 12L)));

        CompraResponseDTO expirada = compraService.procesarPago(creada.id(), true);

        assertThat(expirada.estadoCompra()).isEqualTo("EXPIRADA");
        assertThat(expirada.estadoReintegro()).isEqualTo("COMPLETADO");
        assertThat(expirada.referenciaReintegro()).startsWith("SIM-REF-");
        assertThat(entradaRepository.findAllByCompra_Id(creada.id())).isEmpty();
    }

    @Test
    void procesarPagoDeCompraInexistenteDevuelve404() {
        assertThatThrownBy(() -> compraService.procesarPago(999L, true))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode().value()
                ).isEqualTo(404));
    }

    @Test
    void permiteReventaDeUnAsientoYaDevuelto() {
        CompraResponseDTO primera = crearCompraPagada();
        Entrada entradaDevuelta = entradaRepository.findAllByCompra_Id(primera.id()).stream()
                .filter(entrada -> Long.valueOf(11L).equals(entrada.getIdAsiento()))
                .findFirst()
                .orElseThrow();
        entradaDevuelta.setEstadoEntrada(EstadoEntrada.DEVOLUCION_ACEPTADA);
        entradaRepository.save(entradaDevuelta);

        CompraResponseDTO segunda = crearCompraPagada();

        assertThat(entradaRepository.findAllByCompra_Id(segunda.id()))
                .anySatisfy(entrada -> {
                    assertThat(entrada.getIdAsiento()).isEqualTo(11L);
                    assertThat(entrada.getEstadoEntrada()).isEqualTo(EstadoEntrada.PAGA);
                });
        assertThat(entradaRepository.findAllByIdCliente(ID_CLIENTE).stream()
                .filter(entrada -> Long.valueOf(11L).equals(entrada.getIdAsiento()))
                .count()).isEqualTo(2);
    }

    @Test
    void cancelarEventoNoReintegraEntradasYaDevueltas() {
        CompraResponseDTO creada = crearCompraPagada();
        Entrada devuelta = entradaRepository.findAllByCompra_Id(creada.id()).stream()
                .filter(entrada -> Long.valueOf(11L).equals(entrada.getIdAsiento()))
                .findFirst()
                .orElseThrow();
        devuelta.setEstadoEntrada(EstadoEntrada.DEVOLUCION_ACEPTADA);
        entradaRepository.save(devuelta);

        CancelacionEventoResponseDTO resultado = compraService.cancelarEvento(ID_EVENTO);

        assertThat(resultado.entradasReintegradas()).isEqualTo(1);
        assertThat(resultado.importeReintegrado()).isEqualByComparingTo("1500.00");
        assertThat(entradaRepository.findById(devuelta.getId()).orElseThrow().getEstadoEntrada())
                .isEqualTo(EstadoEntrada.DEVOLUCION_ACEPTADA);
    }

    @Test
    void cancelarEventoReintegraEntradasYCambiaEstados() {
        CompraResponseDTO creada = crearCompraPagada();

        CancelacionEventoResponseDTO resultado = compraService.cancelarEvento(ID_EVENTO);

        assertThat(resultado.comprasCanceladas()).isEqualTo(1);
        assertThat(resultado.entradasReintegradas()).isEqualTo(2);
        assertThat(resultado.importeReintegrado()).isEqualByComparingTo("3000.00");
        assertThat(resultado.inventarioCancelado()).isTrue();

        Compra compra = compraRepository.findById(creada.id()).orElseThrow();
        assertThat(compra.getEstadoCompra()).isEqualTo(EstadoCompra.CANCELADA);
        assertThat(compra.getEstadoReintegro()).isEqualTo(EstadoReintegro.COMPLETADO);
        assertThat(compra.getReferenciaReintegro()).startsWith("SIM-REF-");

        assertThat(entradaRepository.findAllByCompra_Id(creada.id()))
                .allSatisfy(entrada -> assertThat(entrada.getEstadoEntrada())
                        .isEqualTo(EstadoEntrada.EVENTO_CANCELADO));

        verify(reservaClient).cancelarInventario(ID_EVENTO);
    }

    @Test
    void cancelarEventoEsIdempotente() {
        CompraResponseDTO creada = crearCompraPagada();

        compraService.cancelarEvento(ID_EVENTO);
        Compra compraTrasPrimera = compraRepository.findById(creada.id()).orElseThrow();
        String referencia = compraTrasPrimera.getReferenciaReintegro();

        CancelacionEventoResponseDTO segunda = compraService.cancelarEvento(ID_EVENTO);

        assertThat(segunda.comprasCanceladas()).isZero();
        assertThat(segunda.entradasReintegradas()).isZero();
        assertThat(segunda.importeReintegrado()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(compraRepository.findById(creada.id()).orElseThrow().getReferenciaReintegro())
                .isEqualTo(referencia);
    }

    @Test
    void cancelarEventoLiberaComprasPendientesSinReintegro() {
        CompraResponseDTO creada = crearCompraPendiente();
        when(reservaClient.liberar(ID_RESERVA))
                .thenReturn(reserva(ID_RESERVA, "1500.00", "LIBERADA", List.of(11L, 12L)));

        CancelacionEventoResponseDTO resultado = compraService.cancelarEvento(ID_EVENTO);

        assertThat(resultado.comprasCanceladas()).isEqualTo(1);
        assertThat(resultado.entradasReintegradas()).isZero();
        assertThat(resultado.importeReintegrado()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(compraRepository.findById(creada.id()).orElseThrow().getEstadoCompra())
                .isEqualTo(EstadoCompra.CANCELADA);
        assertThat(entradaRepository.findAllByCompra_Id(creada.id())).isEmpty();
    }

    @Test
    void cancelarEventoSinInventarioConfiguradoNoFalla() {
        crearCompraPagada();
        doThrow(feignError(404, "cancelarInventario"))
                .when(reservaClient).cancelarInventario(ID_EVENTO);

        CancelacionEventoResponseDTO resultado = compraService.cancelarEvento(ID_EVENTO);

        assertThat(resultado.inventarioCancelado()).isFalse();
        assertThat(resultado.comprasCanceladas()).isEqualTo(1);
    }

    private CompraResponseDTO crearCompraPendiente() {
        when(eventoClient.obtenerEvento(ID_EVENTO)).thenReturn(eventoFuturo(ID_EVENTO));
        when(reservaClient.reservar(any()))
                .thenReturn(reservaActiva(ID_RESERVA, "1500.00", List.of(11L, 12L)));
        return compraService.crearCompra(
                compraConAsientos("clave-" + System.nanoTime(), ID_CLIENTE, ID_EVENTO, List.of(11L, 12L))
        );
    }

    private CompraResponseDTO crearCompraPagada() {
        CompraResponseDTO creada = crearCompraPendiente();
        when(reservaClient.confirmar(ID_RESERVA))
                .thenReturn(reserva(ID_RESERVA, "1500.00", "CONFIRMADA", List.of(11L, 12L)));
        return compraService.procesarPago(creada.id(), true);
    }

    private static FeignException feignError(int status, String metodo) {
        Request request = Request.create(
                Request.HttpMethod.POST,
                "http://localhost/" + metodo,
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null
        );
        Response response = Response.builder()
                .status(status)
                .request(request)
                .headers(Map.of())
                .build();
        return FeignException.errorStatus(metodo, response);
    }
}
