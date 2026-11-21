package com.apigateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InventarioRoutesTests extends GatewayTestSupport {
    private static final HttpServer backend = backend();
    @Value("${local.server.port}") private int port;
    private final HttpClient client = HttpClient.newHttpClient();

    private static HttpServer backend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] response = (exchange.getRequestMethod() + " " + exchange.getRequestURI())
                        .getBytes(StandardCharsets.UTF_8);
                int status = exchange.getRequestURI().getPath().equals("/asientos/999") ? 409 : 200;
                exchange.sendResponseHeaders(status, response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            });
            server.start();
            return server;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("msvc.sectores.url", () -> "http://127.0.0.1:" + backend.getAddress().getPort());
    }

    @AfterAll
    static void stop() { backend.stop(0); }

    @Test
    void conservaRutasQueryYBody() throws Exception {
        String auth = bearer(token("7", "CLIENTE"));
        for (String path : new String[]{"/sectores?idLugar=1", "/asientos", "/eventos/1/sectores",
                "/eventos/1/sectores/2/disponibilidad", "/eventos/1/inventario"}) {
            assertEquals(200, get(path, auth).statusCode());
        }
        assertEquals(404, get("/reservas", auth).statusCode());
    }

    @Test
    void conservaErrorYNoCapturaCatalogoEventos() throws Exception {
        String auth = bearer(token("7", "CLIENTE"));
        assertEquals(409, get("/asientos/999", auth).statusCode());
        assertEquals(404, get("/eventos/1", auth).statusCode());
    }

    private HttpResponse<String> get(String path, String authorization) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", authorization).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
