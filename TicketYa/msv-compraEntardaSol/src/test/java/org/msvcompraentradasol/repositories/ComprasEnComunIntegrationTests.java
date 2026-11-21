package org.msvcompraentradasol.repositories;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.msvcompraentradasol.JwtTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.msvcompraentradasol.entities.Compra;
import org.msvcompraentradasol.entities.Entrada;
import org.msvcompraentradasol.enums.EstadoCompra;
import org.msvcompraentradasol.enums.EstadoEntrada;
import org.msvcompraentradasol.enums.EstadoReintegro;
import org.msvcompraentradasol.enums.Estadoqr;
import org.msvcompraentradasol.services.RecomendacionCompraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ComprasEnComunIntegrationTests {
    @Autowired CompraRepository compras;
    @Autowired EntradaRepository entradas;
    @Autowired RecomendacionCompraService service;
    @Autowired MockMvc mvc;

    private static final String SERVICIO = JwtTestSupport.bearer(JwtTestSupport.serviceToken(1L));

    private Compra compra(long cliente, long evento, EstadoCompra estado, EstadoEntrada... estados) {
        Compra c = new Compra();
        c.setIdCliente(cliente); c.setIdEvento(evento); c.setEstadoCompra(estado);
        c.setMedioDePago("SIMULADO"); c.setDescripcion("Prueba HU5"); c.setIdSector(1L);
        c.setCantidadEntradas((long) estados.length); c.setClaveIdempotencia(UUID.randomUUID().toString());
        c.setIdReserva(1L); c.setPrecioUnitario(BigDecimal.TEN); c.setImporteTotal(BigDecimal.TEN);
        c = compras.saveAndFlush(c);
        for (var e : estados) entrada(c, e);
        return c;
    }

    private void entrada(Compra compra, EstadoEntrada estado) {
        Entrada e = new Entrada();
        e.setCompra(compra); e.setIdCliente(compra.getIdCliente()); e.setIdEvento(compra.getIdEvento());
        e.setIdSector(1L); e.setPrecio(BigDecimal.TEN); e.setQr(UUID.randomUUID().toString());
        e.setEstadoqr(Estadoqr.NO_ESCANEADO); e.setEstadoEntrada(estado);
        entradas.saveAndFlush(e);
    }

    private void aprobada(long cliente, long evento) {
        compra(cliente, evento, EstadoCompra.APROBADA, EstadoEntrada.PAGA);
    }

    @Test
    void historialValidoDeduplicaYNoIncluyeEventosDeOtrosClientes() throws Exception {
        compra(1, 100, EstadoCompra.APROBADA, EstadoEntrada.PAGA, EstadoEntrada.PAGA);
        aprobada(1, 100); aprobada(1, 200); aprobada(2, 300);
        assertEquals(List.of(100L, 200L), service.historialValido(1L).idsEventos());
        String json = mvc.perform(get("/interno/recomendaciones/clientes/1/historial-valido")
                .header(HttpHeaders.AUTHORIZATION, SERVICIO))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idsEventos.length()").value(2))
                .andExpect(jsonPath("$.idsEventos[0]").value(100)).andReturn().getResponse().getContentAsString();
        assertEquals(1, tools.jackson.databind.json.JsonMapper.builder().build().readTree(json).size());
        mvc.perform(get("/interno/recomendaciones/clientes/0/historial-valido")
                .header(HttpHeaders.AUTHORIZATION, SERVICIO)).andExpect(status().isBadRequest());
        assertTrue(service.historialValido(99L).idsEventos().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCompra.class, mode = EnumSource.Mode.EXCLUDE, names = "APROBADA")
    void historialExcluyeComprasNoAprobadas(EstadoCompra estado) {
        compra(1, 100, estado, EstadoEntrada.PAGA);
        assertTrue(service.historialValido(1L).idsEventos().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(EstadoEntrada.class)
    void historialSoloIncluyeEntradasVigentes(EstadoEntrada estado) {
        compra(1, 100, EstadoCompra.APROBADA, estado);
        boolean vigente = List.of(EstadoEntrada.PAGA, EstadoEntrada.SOLICITUD_DEVOLUCION,
                EstadoEntrada.DEVOLUCION_RECHAZADA).contains(estado);
        assertEquals(vigente ? List.of(100L) : List.of(), service.historialValido(1L).idsEventos());
    }

    @Test
    void historialConservaEventoHastaReintegrarTodasLasEntradasDeTodasLasCompras() {
        Compra primera = compra(1, 100, EstadoCompra.APROBADA, EstadoEntrada.PAGA, EstadoEntrada.DEVOLUCION_ACEPTADA);
        Compra segunda = compra(1, 100, EstadoCompra.APROBADA, EstadoEntrada.PAGA);
        entradas.findAllByCompra_Id(primera.getId()).forEach(e -> e.setEstadoEntrada(EstadoEntrada.DEVOLUCION_ACEPTADA));
        entradas.flush();
        assertEquals(List.of(100L), service.historialValido(1L).idsEventos());
        entradas.findAllByCompra_Id(segunda.getId()).forEach(e -> e.setEstadoEntrada(EstadoEntrada.DEVOLUCION_ACEPTADA));
        entradas.flush();
        assertTrue(service.historialValido(1L).idsEventos().isEmpty());
    }

    @Test
    void cuentaVecinosDistintosSinDuplicarPorEntradasComprasNiEventosCompartidos() {
        aprobada(1, 100); aprobada(1, 200);
        compra(2, 100, EstadoCompra.APROBADA, EstadoEntrada.PAGA, EstadoEntrada.PAGA);
        aprobada(2, 100); aprobada(2, 200);
        compra(2, 300, EstadoCompra.APROBADA, EstadoEntrada.PAGA, EstadoEntrada.PAGA);
        aprobada(2, 300);
        aprobada(3, 100); aprobada(3, 300); aprobada(3, 400);
        aprobada(4, 500); aprobada(4, 300); // No comparte historial: no cuenta.
        long antesCompras = compras.count(), antesEntradas = entradas.count();
        var resultado = service.recomendar(1L);
        assertEquals(List.of(300L, 400L), resultado.stream().map(r -> r.idEvento()).toList());
        assertEquals(2L, resultado.get(0).compradoresEnComun());
        assertEquals(1L, resultado.get(1).compradoresEnComun());
        assertEquals(100L, resultado.get(0).idEventoReferencia());
        assertEquals(antesCompras, compras.count()); assertEquals(antesEntradas, entradas.count());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCompra.class, mode = EnumSource.Mode.EXCLUDE, names = "APROBADA")
    void descartaEstadosNoConfirmadosEnHistorialVecinosYCandidatos(EstadoCompra estado) {
        aprobada(1, 100);
        compra(2, 100, estado, EstadoEntrada.PAGA); aprobada(2, 300);
        aprobada(3, 100); compra(3, 300, estado, EstadoEntrada.PAGA);
        assertTrue(service.recomendar(1L).isEmpty());
        compra(4, 100, estado, EstadoEntrada.PAGA);
        aprobada(5, 100); aprobada(5, 300);
        assertTrue(service.recomendar(4L).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoEntrada.class, names = {"DEVOLUCION_ACEPTADA", "EVENTO_CANCELADO"})
    void reintegradasNoDanHistorialNiVecinosNiCandidatos(EstadoEntrada estado) {
        aprobada(1, 100);
        compra(2, 100, EstadoCompra.APROBADA, estado); aprobada(2, 300);
        aprobada(3, 100); compra(3, 300, EstadoCompra.APROBADA, estado);
        assertTrue(service.recomendar(1L).isEmpty());
        compra(4, 100, EstadoCompra.APROBADA, estado);
        aprobada(5, 100); aprobada(5, 300);
        assertTrue(service.recomendar(4L).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoEntrada.class, names = {"PAGA", "SOLICITUD_DEVOLUCION", "DEVOLUCION_RECHAZADA"})
    void entradasAunNoReintegradasSiguenContando(EstadoEntrada estado) {
        compra(1, 100, EstadoCompra.APROBADA, estado);
        compra(2, 100, EstadoCompra.APROBADA, estado);
        compra(2, 300, EstadoCompra.APROBADA, estado);
        assertEquals(300L, service.recomendar(1L).getFirst().idEvento());
        assertEquals(1L, service.recomendar(1L).getFirst().compradoresEnComun());
    }

    @Test
    void reintegroParcialConservaLaRelacionSiQuedaUnaEntradaVigente() {
        aprobada(1, 100); aprobada(2, 100);
        var parcial = compra(2, 300, EstadoCompra.APROBADA, EstadoEntrada.PAGA, EstadoEntrada.DEVOLUCION_ACEPTADA);
        parcial.setEstadoReintegro(EstadoReintegro.COMPLETADO); compras.saveAndFlush(parcial);
        assertEquals(1L, service.recomendar(1L).getFirst().compradoresEnComun());
        entradas.findAllByCompra_Id(parcial.getId()).forEach(e -> e.setEstadoEntrada(EstadoEntrada.DEVOLUCION_ACEPTADA));
        entradas.flush();
        assertTrue(service.recomendar(1L).isEmpty());
    }

    @Test
    void sinHistorialOCoincidenciasEsListaVaciaYElDesempateEsPorId() {
        assertTrue(service.recomendar(99L).isEmpty());
        aprobada(1, 100); aprobada(2, 500); aprobada(2, 300);
        assertTrue(service.recomendar(1L).isEmpty());
        aprobada(2, 100); aprobada(2, 400);
        assertEquals(List.of(300L, 400L, 500L), service.recomendar(1L).stream().map(r -> r.idEvento()).toList());
    }

    @Test
    void contratoHttpSoloExponeEventosYCantidadesAgregadas() throws Exception {
        aprobada(1, 100); aprobada(2, 100); aprobada(2, 300);
        String body = mvc.perform(get("/interno/recomendaciones/clientes/1")
                .header(HttpHeaders.AUTHORIZATION, SERVICIO)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idEvento").value(300))
                .andExpect(jsonPath("$[0].compradoresEnComun").value(1))
                .andExpect(jsonPath("$[0].idEventoReferencia").value(100))
                .andExpect(jsonPath("$[0].idCliente").doesNotExist())
                .andExpect(jsonPath("$[0].idCompra").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertEquals(3, tools.jackson.databind.json.JsonMapper.builder().build().readTree(body).get(0).size());
        mvc.perform(get("/interno/recomendaciones/clientes/0")
                .header(HttpHeaders.AUTHORIZATION, SERVICIO)).andExpect(status().isBadRequest());
    }
}
