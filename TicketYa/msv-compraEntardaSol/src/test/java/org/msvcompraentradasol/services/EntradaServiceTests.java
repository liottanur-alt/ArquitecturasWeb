package org.msvcompraentradasol.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.msvcompraentradasol.dtos.EntradaResponseDTO;
import org.msvcompraentradasol.entities.Compra;
import org.msvcompraentradasol.entities.Entrada;
import org.msvcompraentradasol.enums.EstadoCompra;
import org.msvcompraentradasol.enums.EstadoEntrada;
import org.msvcompraentradasol.enums.Estadoqr;
import org.msvcompraentradasol.repositories.CompraRepository;
import org.msvcompraentradasol.repositories.EntradaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class EntradaServiceTests {

    @Autowired
    private EntradaService entradaService;

    @Autowired
    private CompraRepository compraRepository;

    @Autowired
    private EntradaRepository entradaRepository;

    private Compra compra;

    @BeforeEach
    void prepararCompra() {
        entradaRepository.deleteAll();
        compraRepository.deleteAll();
        compra = compraRepository.save(compraDePrueba("compra-entradas"));
    }

    @Test
    void crearEntradaGeneraQrYEstadoInicial() {
        Entrada entrada = entradaService.crearEntrada(
                2L, 1L, 3L, 11L, new BigDecimal("1500.00"), compra
        );

        assertThat(entrada.getId()).isNotNull();
        assertThat(entrada.getQr()).isNotBlank();
        assertThat(entrada.getEstadoEntrada()).isEqualTo(EstadoEntrada.PAGA);
        assertThat(entrada.getEstadoqr()).isEqualTo(Estadoqr.NO_ESCANEADO);
        assertThat(entrada.getCompra().getId()).isEqualTo(compra.getId());
    }

    @Test
    void buscarPorIdDevuelveContrato() {
        Entrada entrada = crearEntrada(11L);

        EntradaResponseDTO respuesta = entradaService.buscarPorId(entrada.getId());

        assertThat(respuesta.id()).isEqualTo(entrada.getId());
        assertThat(respuesta.qr()).isEqualTo(entrada.getQr());
        assertThat(respuesta.estadoEntrada()).isEqualTo("PAGA");
        assertThat(respuesta.estadoQr()).isEqualTo("NO_ESCANEADO");
        assertThat(respuesta.idAsiento()).isEqualTo(11L);
    }

    @Test
    void buscarPorIdInexistenteDevuelve404() {
        assertThatThrownBy(() -> entradaService.buscarPorId(999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> assertThat(
                        ((ResponseStatusException) exception).getStatusCode().value()
                ).isEqualTo(404));
    }

    @Test
    void buscarPorQrDevuelveLaEntrada() {
        Entrada entrada = crearEntrada(11L);

        EntradaResponseDTO respuesta = entradaService.buscarPorQr(entrada.getQr());

        assertThat(respuesta.id()).isEqualTo(entrada.getId());
    }

    @Test
    void marcarUsadaCambiaElEstadoDelQr() {
        Entrada entrada = crearEntrada(11L);

        EntradaResponseDTO respuesta = entradaService.marcarUsada(entrada.getId());

        assertThat(respuesta.estadoQr()).isEqualTo("ESCANEADO");
        assertThat(entradaRepository.findById(entrada.getId()).orElseThrow().getEstadoqr())
                .isEqualTo(Estadoqr.ESCANEADO);
    }

    @Test
    void marcarUsadaPorQrFuncionaUnaSolaVez() {
        Entrada entrada = crearEntrada(11L);

        entradaService.marcarUsadaPorQr(entrada.getQr());

        assertThatThrownBy(() -> entradaService.marcarUsadaPorQr(entrada.getQr()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("utilizada");
    }

    @Test
    void marcarEntradaNoVigenteFalla() {
        Entrada entrada = crearEntrada(11L);
        entrada.setEstadoEntrada(EstadoEntrada.DEVOLUCION_ACEPTADA);
        entradaRepository.save(entrada);

        assertThatThrownBy(() -> entradaService.marcarUsada(entrada.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vigente");
    }

    @Test
    void listarPorCompraYPorCliente() {
        crearEntrada(11L);
        crearEntrada(12L);

        List<EntradaResponseDTO> porCompra = entradaService.listarPorCompra(compra.getId());
        List<EntradaResponseDTO> porCliente = entradaService.listarPorCliente(compra.getIdCliente());

        assertThat(porCompra).hasSize(2);
        assertThat(porCliente).hasSize(2);
        assertThat(porCompra).extracting(EntradaResponseDTO::idAsiento)
                .containsExactlyInAnyOrder(11L, 12L);
    }

    private Entrada crearEntrada(Long idAsiento) {
        return entradaService.crearEntrada(
                compra.getIdCliente(),
                compra.getIdEvento(),
                compra.getIdSector(),
                idAsiento,
                new BigDecimal("1500.00"),
                compra
        );
    }

    private static Compra compraDePrueba(String clave) {
        Compra nueva = new Compra();
        nueva.setIdCliente(2L);
        nueva.setMedioDePago("TARJETA");
        nueva.setEstadoCompra(EstadoCompra.APROBADA);
        nueva.setIdEvento(1L);
        nueva.setDescripcion("Compra de prueba");
        nueva.setCantidadEntradas(2L);
        nueva.setIdSector(3L);
        nueva.setClaveIdempotencia(clave);
        nueva.setIdReserva(55L);
        nueva.setPrecioUnitario(new BigDecimal("1500.00"));
        nueva.setImporteTotal(new BigDecimal("3000.00"));
        return nueva;
    }
}
