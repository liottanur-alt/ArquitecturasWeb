package com.universidad.msvsectoerasiento;

import com.universidad.msvsectoerasiento.dto.*;
import com.universidad.msvsectoerasiento.enums.*;
import com.universidad.msvsectoerasiento.service.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

/** Prueba transacciones reales y varias instancias logicas sin simular los repositorios. */
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@SpringBootTest @ActiveProfiles("test")
@Import(InventarioIntegrationTests.RelojPrueba.class)
class InventarioIntegrationTests {
    static final Instant BASE = Instant.parse("2030-01-01T12:00:00Z");
    static final AtomicLong SECUENCIA = new AtomicLong(100);
    static final HttpServer REMOTO = iniciarProveedor();
    @Autowired InventarioService inventario;
    @Autowired SectorService sectores;
    @Autowired AsientoService asientos;
    @Autowired RelojMutable reloj;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;

    @TestConfiguration
    static class RelojPrueba {
        @Bean @Primary RelojMutable tiempoPrueba() { return new RelojMutable(); }
    }
    static class RelojMutable extends Clock {
        Instant ahora = BASE;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return ahora; }
    }

    /** Proveedor HTTP de prueba: verifica que Feign envie y decodifique el contrato esperado. */
    static HttpServer iniciarProveedor() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/eventos/", exchange -> {
                long id = Long.parseLong(exchange.getRequestURI().getPath().substring("/api/eventos/".length()));
                String json = """
                    {"id":%d,"lugar":{"id":1,"nombre":"Teatro","ciudad":"Tandil"},"descripcion":"Contrato real","participantes":[],"nombre":"Prueba","idOrganizador":1,
                     "fechaHora":"2030-01-11T09:00:00","estado":"PUBLICADO","estadoPublicacion":"PUBLICADO"}
                    """.formatted(id);
                if (id == 99999) json = "{}";
                byte[] body = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(id == 99999 ? 404 : 200, body.length);
                exchange.getResponseBody().write(body); exchange.close();
            });
            server.start(); return server;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource
    static void urls(DynamicPropertyRegistry properties) {
        properties.add("msvc.eventos.url", () -> "http://127.0.0.1:" + REMOTO.getAddress().getPort());
    }
    @AfterAll static void cerrar() { REMOTO.stop(0); }
    @BeforeEach void reiniciarReloj() { reloj.ahora = BASE; }

    record Escenario(Long evento, Long sector, List<Long> asientos) {}

    /** Crea inventario usando los servicios reales y el proveedor HTTP de Eventos. */
    Escenario preparar(TipoSector tipo, int capacidad, int limite) {
        long evento = SECUENCIA.incrementAndGet();
        var sector = new SectorRequest(); sector.setIdLugar(1L); sector.setNombre("Sector " + evento);
        sector.setTipo(tipo); sector.setCapacidadTotal(capacidad);
        long idSector = sectores.crear(sector).id();
        List<Long> ids = new ArrayList<>();
        if (tipo == TipoSector.NUMERADO) {
            for (int i = 0; i < capacidad; i++) {
                var a = new AsientoRequest(); a.setIdSector(idSector); a.setFila("A"); a.setColumna("" + i);
                ids.add(asientos.crear(a).id());
            }
        }
        inventario.configurarEvento(evento, new InventarioRequest(limite));
        inventario.configurarSector(evento, new SectorEventoRequest(idSector, new BigDecimal("1500.00"), capacidad));
        return new Escenario(evento, idSector, ids);
    }

    ReservaRequest pedido(Escenario e, String clave, long cliente, int cantidad, List<Long> ids) {
        return new ReservaRequest(clave, cliente, e.evento(), e.sector(), cantidad, ids);
    }
    String clave() { return UUID.randomUUID().toString(); }

    @Test void endpointsValidanYNoCachean() throws Exception {
        var e = preparar(TipoSector.CAMPO, 2, 4);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/eventos/" + e.evento() + "/sectores/" + e.sector() + "/disponibilidad"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/reservas")
                .contentType("application/json").content("{}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        String json = """
            {"claveIdempotencia":"%s","idCliente":1,"idEvento":%d,"idSector":%d,"cantidad":1,"idAsientos":[]}
            """.formatted(clave(), e.evento(), e.sector());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/reservas")
                .contentType("application/json").content(json))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.estado").value("ACTIVA"));
    }

    @Test void circuitoNumeradoYReintentos() {
        var e = preparar(TipoSector.NUMERADO, 2, 4);
        var q = pedido(e, clave(), 1, 1, List.of(e.asientos().get(0)));
        var r = inventario.reservar(q);
        assertThat(inventario.reservar(q).id()).isEqualTo(r.id());
        assertThat(inventario.confirmar(r.id()).estado()).isEqualTo(EstadoReserva.CONFIRMADA);
        inventario.confirmar(r.id());
        var d = inventario.consultarSector(e.evento(), e.sector());
        assertThat(d.vendidos()).isEqualTo(1); assertThat(d.disponibles()).isEqualTo(1);
        assertThatThrownBy(() -> inventario.liberar(r.id())).isInstanceOf(ResponseStatusException.class);
    }

    @Test void campoLiberacionYVencimiento() {
        var e = preparar(TipoSector.CAMPO, 2, 4);
        var r = inventario.reservar(pedido(e, clave(), 1, 1, List.of()));
        inventario.liberar(r.id()); inventario.liberar(r.id());
        var otro = inventario.reservar(pedido(e, clave(), 2, 2, List.of()));
        reloj.ahora = BASE.plus(Duration.ofMinutes(10));
        assertThat(inventario.confirmar(otro.id()).estado()).isEqualTo(EstadoReserva.VENCIDA);
        assertThat(inventario.consultarSector(e.evento(), e.sector()).disponibles()).isEqualTo(2);
    }

    @Test void devolucionParcialIdempotenteNoAfectaReventa() {
        var e = preparar(TipoSector.NUMERADO, 2, 4);
        var r = inventario.reservar(pedido(e, clave(), 1, 2, e.asientos())); inventario.confirmar(r.id());
        var d = new DevolucionRequest(clave(), 1, BASE, List.of(e.asientos().get(0)));
        inventario.devolver(r.id(), d);
        var reventa = inventario.reservar(pedido(e, clave(), 2, 1, List.of(e.asientos().get(0))));
        inventario.devolver(r.id(), d);
        assertThat(inventario.consultarSector(e.evento(), e.sector()).reservados()).isEqualTo(1);
        assertThatThrownBy(() -> inventario.devolver(r.id(), new DevolucionRequest(clave(), 1, BASE, d.idAsientos())))
            .isInstanceOf(ResponseStatusException.class);
        inventario.confirmar(reventa.id());
        inventario.cancelar(e.evento()); inventario.cancelar(e.evento());
        assertThat(inventario.consultarSector(e.evento(), e.sector()).vendidos()).isZero();
        assertThat(inventario.consultarSector(e.evento(), e.sector()).reservados()).isZero();
    }

    @Test void limiteEntreSectoresYClaveConOtroContenido() {
        var e = preparar(TipoSector.CAMPO, 4, 1);
        var sectorExtra = new SectorRequest(); sectorExtra.setIdLugar(1L); sectorExtra.setNombre("Otro campo");
        sectorExtra.setTipo(TipoSector.CAMPO); sectorExtra.setCapacidadTotal(2);
        var extra = sectores.crear(sectorExtra);
        inventario.configurarSector(e.evento(), new SectorEventoRequest(extra.id(), new BigDecimal("100"), 2));
        var q = pedido(e, clave(), 1, 1, List.of()); var r = inventario.reservar(q);
        assertThatThrownBy(() -> inventario.reservar(new ReservaRequest(clave(), 1L, e.evento(), extra.id(), 1, List.of())))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> inventario.reservar(pedido(e, clave(), 1, 1, List.of()))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> inventario.reservar(pedido(e, q.claveIdempotencia(), 2, 1, List.of()))).isInstanceOf(ResponseStatusException.class);
        inventario.liberar(r.id());
        assertThat(inventario.reservar(pedido(e, clave(), 1, 1, List.of())).estado()).isEqualTo(EstadoReserva.ACTIVA);
    }

    @Test void cancelacionImpideNuevasReservas() {
        var e = preparar(TipoSector.CAMPO, 3, 4);
        var r = inventario.reservar(pedido(e, clave(), 1, 1, List.of()));
        inventario.cancelar(e.evento());
        assertThat(inventario.obtenerReserva(r.id()).estado()).isEqualTo(EstadoReserva.LIBERADA);
        assertThatThrownBy(() -> inventario.reservar(pedido(e, clave(), 2, 1, List.of()))).isInstanceOf(ResponseStatusException.class);
    }

    @Test void plazoSeEvaluaSobreSolicitudNoSobreAprobacion() {
        var e = preparar(TipoSector.CAMPO, 2, 4);
        var r = inventario.reservar(pedido(e, clave(), 1, 2, List.of())); inventario.confirmar(r.id());
        reloj.ahora = BASE.plus(Duration.ofDays(9));
        assertThatThrownBy(() -> inventario.devolver(r.id(), new DevolucionRequest(clave(), 1, reloj.instant(), List.of())))
            .isInstanceOf(ResponseStatusException.class);
        assertThat(inventario.devolver(r.id(), new DevolucionRequest(clave(), 1, BASE, List.of())).cantidadDevuelta()).isEqualTo(1);
    }

    @Test void servicioEventos404SeTraduce() {
        assertThatThrownBy(() -> inventario.configurarEvento(99999L, new InventarioRequest(4)))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    /** Varias transacciones independientes compiten por el ultimo lugar: solo una gana. */
    @Test void concurrenciaMismoAsiento() throws Exception { competir(TipoSector.NUMERADO, false); }
    @Test void concurrenciaUltimoCampo() throws Exception { competir(TipoSector.CAMPO, false); }
    @Test void concurrenciaMismaClaveRecuperaUnaSolaReserva() throws Exception { competir(TipoSector.CAMPO, true); }

    void competir(TipoSector tipo, boolean mismaClave) throws Exception {
        var e = preparar(tipo, 1, 4); String fija = clave();
        var pool = Executors.newFixedThreadPool(12); var inicio = new CountDownLatch(1);
        List<Future<Long>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < 12; i++) {
                final int cliente = i + 1;
                resultados.add(pool.submit(() -> {
                    inicio.await();
                    try {
                        var r = inventario.reservar(pedido(e, mismaClave ? fija : clave(), mismaClave ? 1 : cliente, 1, e.asientos()));
                        inventario.confirmar(r.id()); return r.id();
                    }
                    catch (ResponseStatusException error) {
                        if (error.getStatusCode().value() != 409) throw error;
                        return null;
                    }
                }));
            }
            inicio.countDown(); Set<Long> ids = new HashSet<>(); int exitos = 0;
            for (var resultado : resultados) {
                Long id = resultado.get(30, TimeUnit.SECONDS); if (id != null) { ids.add(id); exitos++; }
            }
            assertThat(ids).hasSize(1); assertThat(exitos).isEqualTo(mismaClave ? 12 : 1);
            assertThat(inventario.consultarSector(e.evento(), e.sector()).vendidos()).isEqualTo(1);
            assertThat(inventario.consultarSector(e.evento(), e.sector()).reservados()).isZero();
        } finally { pool.shutdownNow(); }
    }
}
