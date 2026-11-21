package org.msvcompraentradasol.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.msvcompraentradasol.JwtTestSupport;
import org.msvcompraentradasol.MsvCompraEntradaSolApplication;
import org.msvcompraentradasol.entities.Compra;
import org.msvcompraentradasol.entities.Entrada;
import org.msvcompraentradasol.enums.EstadoCompra;
import org.msvcompraentradasol.enums.EstadoEntrada;
import org.msvcompraentradasol.enums.Estadoqr;
import org.msvcompraentradasol.repositories.CompraRepository;
import org.msvcompraentradasol.repositories.EntradaRepository;
import org.msvcompraentradasol.repositories.SolicitudRepository;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/** Ejercita la comunicacion Feign real de la cancelacion: sin mocks de EventoClientFeign. */
class CancelacionEventoFeignIntegrationTests {

    private static final long ID_EVENTO = 7L;
    private static final String TOKEN = JwtTestSupport.bearer(JwtTestSupport.token("101", "CLIENTE"));
    private static final AtomicReference<String> solicitudEventos = new AtomicReference<>();
    private static final AtomicInteger patchesEventos = new AtomicInteger();
    private static final AtomicInteger cancelacionesInventario = new AtomicInteger();
    private static final AtomicInteger estadoEventos = new AtomicInteger(200);
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final HttpServer EVENTOS = iniciarEventos();
    private static final HttpServer SECTORES = iniciarSectores();
    private static ConfigurableApplicationContext aplicacion;
    private static int puerto;

    @BeforeAll
    static void iniciarAplicacionCompras() {
        aplicacion = new SpringApplicationBuilder(MsvCompraEntradaSolApplication.class)
                .profiles("test")
                .run("--server.port=0", "--msvc.eventos.url=" + url(EVENTOS),
                        "--msvc.sectores.url=" + url(SECTORES));
        puerto = ((ServletWebServerApplicationContext) aplicacion).getWebServer().getPort();
    }

    @BeforeEach
    void limpiar() {
        aplicacion.getBean(SolicitudRepository.class).deleteAll();
        aplicacion.getBean(EntradaRepository.class).deleteAll();
        aplicacion.getBean(CompraRepository.class).deleteAll();
        solicitudEventos.set(null);
        patchesEventos.set(0);
        cancelacionesInventario.set(0);
        estadoEventos.set(200);
    }

    @AfterAll
    static void cerrarServidores() {
        if (aplicacion != null) aplicacion.close();
        EVENTOS.stop(0);
        SECTORES.stop(0);
    }

    @Test
    void elPatchLlegaAEventosYLaCancelacionTermina() throws Exception {
        Long idCompra = sembrarCompraAprobadaConEntrada();

        HttpResponse<String> respuesta = cancelar();

        assertThat(respuesta.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(solicitudEventos.get()).isEqualTo("PATCH /api/eventos/" + ID_EVENTO + "/cancelar");
        assertThat(patchesEventos).hasValue(1);
        assertThat(cancelacionesInventario).hasValue(1);
        assertThat(respuesta.body()).contains("\"comprasCanceladas\":1", "\"entradasReintegradas\":1");
        assertThat(aplicacion.getBean(CompraRepository.class).findById(idCompra).orElseThrow()
                .getEstadoCompra()).isEqualTo(EstadoCompra.CANCELADA);
        assertThat(aplicacion.getBean(EntradaRepository.class).findAllByCompra_Id(idCompra))
                .allSatisfy(entrada -> assertThat(entrada.getEstadoEntrada())
                        .isEqualTo(EstadoEntrada.EVENTO_CANCELADO));
    }

    @Test
    void falloDeEventosSeMapeaA502YNoCancelaLaCompra() throws Exception {
        Long idCompra = sembrarCompraAprobadaConEntrada();
        estadoEventos.set(500);

        HttpResponse<String> respuesta = cancelar();

        assertThat(respuesta.statusCode()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(respuesta.body()).contains("Un servicio requerido devolvió un error");
        assertThat(patchesEventos.get()).isPositive();
        assertThat(cancelacionesInventario).hasValue(0);
        assertThat(aplicacion.getBean(CompraRepository.class).findById(idCompra).orElseThrow()
                .getEstadoCompra()).isEqualTo(EstadoCompra.APROBADA);
    }

    private HttpResponse<String> cancelar() throws Exception {
        return HTTP.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + puerto + "/compras/eventos/" + ID_EVENTO + "/cancelacion"))
                        .timeout(Duration.ofSeconds(20))
                        .header(HttpHeaders.AUTHORIZATION, TOKEN)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private Long sembrarCompraAprobadaConEntrada() {
        Compra compra = new Compra();
        compra.setIdCliente(101L);
        compra.setIdEvento(ID_EVENTO);
        compra.setIdSector(3L);
        compra.setEstadoCompra(EstadoCompra.APROBADA);
        compra.setMedioDePago("TARJETA");
        compra.setCantidadEntradas(1L);
        compra.setClaveIdempotencia(UUID.randomUUID().toString());
        compra.setIdReserva(55L);
        compra.setPrecioUnitario(BigDecimal.valueOf(1500));
        compra.setImporteTotal(BigDecimal.valueOf(1500));
        compra.setDescripcion("Compra de prueba");
        compra.setReferenciaPago("SIM-PAGO");
        compra = aplicacion.getBean(CompraRepository.class).saveAndFlush(compra);

        Entrada entrada = new Entrada();
        entrada.setCompra(compra);
        entrada.setIdCliente(101L);
        entrada.setIdEvento(ID_EVENTO);
        entrada.setIdSector(3L);
        entrada.setPrecio(BigDecimal.valueOf(1500));
        entrada.setQr(UUID.randomUUID().toString());
        entrada.setEstadoqr(Estadoqr.NO_ESCANEADO);
        entrada.setEstadoEntrada(EstadoEntrada.PAGA);
        aplicacion.getBean(EntradaRepository.class).saveAndFlush(entrada);
        return compra.getId();
    }

    private static HttpServer iniciarEventos() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/", exchange -> {
                String metodo = exchange.getRequestMethod();
                String ruta = exchange.getRequestURI().getPath();
                solicitudEventos.set(metodo + " " + ruta);
                if ("PATCH".equals(metodo) && ruta.equals("/api/eventos/" + ID_EVENTO + "/cancelar")) {
                    patchesEventos.incrementAndGet();
                    responder(exchange, estadoEventos.get(), "{}");
                    return;
                }
                responder(exchange, 404, "{}");
            });
            servidor.start();
            return servidor;
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo iniciar el servidor de Eventos", exception);
        }
    }

    private static HttpServer iniciarSectores() {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/", exchange -> {
                String metodo = exchange.getRequestMethod();
                String ruta = exchange.getRequestURI().getPath();
                if ("POST".equals(metodo)
                        && ruta.equals("/eventos/" + ID_EVENTO + "/inventario/cancelacion")) {
                    cancelacionesInventario.incrementAndGet();
                    exchange.sendResponseHeaders(204, -1);
                    exchange.close();
                    return;
                }
                responder(exchange, 404, "{}");
            });
            servidor.start();
            return servidor;
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo iniciar el servidor de Sectores", exception);
        }
    }

    private static void responder(HttpExchange exchange, int estado, String cuerpo) throws java.io.IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(estado, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String url(HttpServer servidor) {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }
}
