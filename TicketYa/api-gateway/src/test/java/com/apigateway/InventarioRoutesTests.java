package com.apigateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventarioRoutesTests {
    static final HttpServer BACKEND = iniciar();
    @Value("${local.server.port}") int puerto;
    final HttpClient cliente = HttpClient.newHttpClient();

    static HttpServer iniciar() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", e -> {
                var entrada = new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                var body = (e.getRequestMethod() + " " + e.getRequestURI() + "|" + entrada).getBytes(StandardCharsets.UTF_8);
                e.sendResponseHeaders(e.getRequestURI().getPath().equals("/asientos/999") ? 409 : 200, body.length);
                e.getResponseBody().write(body); e.close();
            });
            server.start(); return server;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    @DynamicPropertySource static void urls(DynamicPropertyRegistry r) {
        r.add("msvc.sectores.url", () -> "http://127.0.0.1:" + BACKEND.getAddress().getPort());
    }
    @AfterAll static void cerrar() { BACKEND.stop(0); }

    @Test void conservaRutasQueryYBody() throws Exception {
        for (String path : new String[]{"/sectores?idLugar=1", "/asientos", "/reservas/1",
                "/eventos/1/sectores", "/eventos/1/sectores/2/disponibilidad", "/eventos/1/inventario"}) {
            var response = cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode()); assertEquals("GET " + path + "|", response.body());
        }
        var response = cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/reservas"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}" )).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode()); assertEquals("POST /reservas|{}", response.body());
    }
    @Test void conservaErrorYNoCapturaCatalogoEventos() throws Exception {
        var error = cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/asientos/999")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(409, error.statusCode());
        var evento = cliente.send(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/eventos/1")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, evento.statusCode());
    }
}
