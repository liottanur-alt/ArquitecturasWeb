package org.msvcompraentradasol.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.msvcompraentradasol.entities.Compra;
import org.msvcompraentradasol.MsvCompraEntradaSolApplication;
import org.msvcompraentradasol.repositories.CompraRepository;
import org.msvcompraentradasol.repositories.EntradaRepository;
import org.msvcompraentradasol.repositories.SolicitudRepository;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;

class CompraFilaFeignIntegrationTests {

    private static final long CLIENTE_EN_FILA = 101L;
    private static final String AUTORIZACION = "autorizacion-101";
    private static final AtomicBoolean operacionValidada = new AtomicBoolean();
    private static final AtomicBoolean siguienteAdmitido = new AtomicBoolean();
    private static final AtomicInteger reservasIniciadas = new AtomicInteger();
    private static final AtomicReference<String> idOperacionValidada = new AtomicReference<>();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final HttpServer EVENTOS = iniciarServidor(true);
    private static final HttpServer SECTORES = iniciarServidor(false);
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
    void limpiarYPrepararCola() {
        aplicacion.getBean(SolicitudRepository.class).deleteAll();
        aplicacion.getBean(EntradaRepository.class).deleteAll();
        aplicacion.getBean(CompraRepository.class).deleteAll();
        operacionValidada.set(false);
        siguienteAdmitido.set(false);
        reservasIniciadas.set(0);
        idOperacionValidada.set(null);
    }

    @AfterAll
    static void cerrarServidores() {
        if (aplicacion != null) aplicacion.close();
        EVENTOS.stop(0);
        SECTORES.stop(0);
    }

    @Test
    void compraAdmitidaValidaPagaYCompletaElTurnoParaAdmitirAlSiguiente() throws Exception {
        String clave = "fila-" + UUID.randomUUID();
        String solicitud = solicitud(clave, AUTORIZACION);

        HttpResponse<String> creada = HTTP.send(
                HttpRequest.newBuilder(uri("/compras"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(solicitud))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(creada.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(campo(creada.body(), "estadoCompra")).isEqualTo("PENDIENTE");
        assertThat(operacionValidada).isTrue();
        assertThat(reservasIniciadas).hasValue(1);
        Long idCompra = Long.valueOf(campo(creada.body(), "id"));

        HttpResponse<String> pagada = HTTP.send(
                HttpRequest.newBuilder(uri("/compras/" + idCompra + "/pago?aprobado=true"))
                        .timeout(Duration.ofSeconds(15))
                        .PUT(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(pagada.statusCode()).isEqualTo(HttpStatus.OK.value());
        assertThat(campo(pagada.body(), "estadoCompra")).isEqualTo("APROBADA");
        assertThat(aplicacion.getBean(EntradaRepository.class).findAllByCompra_Id(idCompra)).hasSize(1);
        assertThat(aplicacion.getBean(CompraRepository.class).findById(idCompra))
                .get().extracting(Compra::getAutorizacionCompra).isEqualTo(AUTORIZACION);
        assertThat(siguienteAdmitido).as("el cierre del turno debe admitir a quien esperaba").isTrue();
    }

    @Test
    void compraDeAltaDemandaSinAutorizacionSeRechazaAntesDeReservar() throws Exception {
        HttpResponse<String> respuesta = HTTP.send(
                HttpRequest.newBuilder(uri("/compras"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(solicitud("sin-fila-" + UUID.randomUUID(), null)))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(respuesta.statusCode()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(reservasIniciadas).hasValue(0);
    }

    private static String solicitud(String clave, String autorizacion) {
        String codigo = autorizacion == null ? "null" : "\"" + autorizacion + "\"";
        return "{\"claveIdempotencia\":\"" + clave + "\",\"idCliente\":" + CLIENTE_EN_FILA
                + ",\"idEvento\":1,\"idSector\":3,\"cantidadEntradas\":1,\"medioDePago\":\"TARJETA\","
                + "\"autorizacionCompra\":" + codigo + "}";
    }

    private static HttpServer iniciarServidor(boolean eventos) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                try {
                    if (eventos) responderEventos(exchange, path, body);
                    else responderSectores(exchange, path);
                } catch (Exception exception) {
                    byte[] error = exception.getMessage().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, error.length);
                    exchange.getResponseBody().write(error);
                    exchange.close();
                }
            });
            server.start();
            return server;
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo iniciar el servidor de prueba", exception);
        }
    }

    private static void responderEventos(
            com.sun.net.httpserver.HttpExchange exchange, String path, String body
    ) throws Exception {
        if ("GET".equals(exchange.getRequestMethod()) && path.equals("/api/eventos/interno/1")) {
            String evento = """
                    {"id":1,"fechaHora":"%s","estado":"ACTIVO","estadoPublicacion":"PUBLICADO","lugar":{"id":9}}
                    """.formatted(LocalDateTime.now().plusDays(30));
            responder(exchange, 200, evento);
            return;
        }
        if ("POST".equals(exchange.getRequestMethod())
                && path.equals("/interno/fila/autorizaciones/validar")) {
            boolean valida = "1".equals(campo(body, "eventoId"))
                    && Long.toString(CLIENTE_EN_FILA).equals(campo(body, "clienteId"))
                    && AUTORIZACION.equals(campo(body, "autorizacionCompra"))
                    && campo(body, "idOperacion") != null;
            operacionValidada.set(valida);
            if (valida) idOperacionValidada.set(campo(body, "idOperacion"));
            responder(exchange, 200, valida
                    ? "{\"autorizado\":true,\"requiereFila\":true,\"motivo\":\"AUTORIZADO\"}"
                    : "{\"autorizado\":false,\"requiereFila\":true,\"motivo\":\"AUTORIZACION_Y_OPERACION_REQUERIDAS\"}");
            return;
        }
        if ("POST".equals(exchange.getRequestMethod())
                && path.equals("/interno/fila/autorizaciones/completar")) {
            boolean completa = operacionValidada.get()
                    && "1".equals(campo(body, "eventoId"))
                    && Long.toString(CLIENTE_EN_FILA).equals(campo(body, "clienteId"))
                    && AUTORIZACION.equals(campo(body, "autorizacionCompra"));
            boolean mismaOperacion = completa
                    && idOperacionValidada.get().equals(campo(body, "idOperacion"));
            siguienteAdmitido.set(mismaOperacion);
            responder(exchange, 200, mismaOperacion
                    ? "{\"autorizado\":true,\"requiereFila\":true,\"motivo\":\"COMPRA_COMPLETADA\"}"
                    : "{\"autorizado\":false,\"requiereFila\":true,\"motivo\":\"OPERACION_DISTINTA\"}");
            return;
        }
        responder(exchange, 404, "{}");
    }

    private static void responderSectores(com.sun.net.httpserver.HttpExchange exchange, String path)
            throws Exception {
        if ("POST".equals(exchange.getRequestMethod()) && path.equals("/reservas")) {
            reservasIniciadas.incrementAndGet();
            responder(exchange, 200, reserva("ACTIVA"));
            return;
        }
        if ("POST".equals(exchange.getRequestMethod()) && path.equals("/reservas/55/confirmacion")) {
            responder(exchange, 200, reserva("CONFIRMADA"));
            return;
        }
        responder(exchange, 404, "{}");
    }

    private static String reserva(String estado) {
        return """
                {"id":55,"precioUnitario":1500.00,"venceEn":"%s","estado":"%s","idAsientos":[]}
                """.formatted(Instant.now().plusSeconds(900), estado);
    }

    private static void responder(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + puerto + path);
    }

    private static String campo(String json, String nombre) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(nombre)
                + "\\\"\\s*:\\s*(?:\\\"([^\\\"]*)\\\"|(null)|(-?\\d+))").matcher(json);
        if (!matcher.find() || matcher.group(2) != null) return null;
        return matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
    }
}
