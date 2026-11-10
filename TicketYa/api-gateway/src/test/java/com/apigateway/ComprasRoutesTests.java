package com.apigateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ComprasRoutesTests {
    private static final HttpServer backend = backend();
    @Value("${local.server.port}") private int port;
    private final HttpClient client = HttpClient.newHttpClient();

    private static HttpServer backend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                byte[] response = (exchange.getRequestMethod() + " " + exchange.getRequestURI()
                        + "|" + body).getBytes(StandardCharsets.UTF_8);
                int status = exchange.getRequestURI().getPath().equals("/compras/999") ? 404 : 200;
                exchange.sendResponseHeaders(status, response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            });
            server.start();
            return server;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("msvc.compras.url", () -> "http://127.0.0.1:" + backend.getAddress().getPort());
    }

    @AfterAll
    static void stop() { backend.stop(0); }

    @Test
    void comprasEntradasYSolicitudesLleganAlMicroservicio() throws Exception {
        for (String path : new String[]{"/compras/1", "/compras/cliente/2", "/entradas/1",
                "/entradas/qr/abc", "/entradas/compra/1", "/solicitudes/1", "/solicitudes/pendientes"}) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("GET " + path + "|", response.body());
        }
    }

    @Test
    void conservaMetodoQueryYBody() throws Exception {
        var pago = client.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/compras/1/pago?aprobado=true"))
                .PUT(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, pago.statusCode());
        assertEquals("PUT /compras/1/pago?aprobado=true|", pago.body());

        var compra = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/compras"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"idCliente\":2}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, compra.statusCode());
        assertEquals("POST /compras|{\"idCliente\":2}", compra.body());

        var uso = client.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/entradas/1/uso"))
                .method("PATCH", HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, uso.statusCode());
        assertEquals("PATCH /entradas/1/uso|", uso.body());
    }

    @Test
    void cancelacionDeEventoVaAComprasNoASectores() throws Exception {
        var response = client.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/compras/eventos/1/cancelacion"))
                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("POST /compras/eventos/1/cancelacion|", response.body());
    }

    @Test
    void conserva404DelMicroservicio() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/compras/999"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
    }
}
