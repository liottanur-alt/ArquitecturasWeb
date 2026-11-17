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
class UsuarioRoutesTests {
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
                int status = exchange.getRequestURI().getPath().equals("/usuarios/999") ? 404 : 200;
                exchange.getResponseHeaders().add("X-Historial-Verificado", "false");
                exchange.getResponseHeaders().add("X-Disponibilidad-Verificada", "false");
                exchange.getResponseHeaders().add("X-Respuesta-Parcial", "true");
                exchange.sendResponseHeaders(status, response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            });
            server.start();
            return server;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("msvc.usuarios.url", () -> "http://127.0.0.1:" + backend.getAddress().getPort());
    }

    @AfterAll
    static void stop() { backend.stop(0); }

    @Test
    void creacionDeUsuariosYRolesLlegaSinTokenYConBody() throws Exception {
        for (String path : new String[]{"/usuarios", "/roles"}) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"ana@example.com\"}")).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("POST " + path + "|{\"email\":\"ana@example.com\"}", response.body());
        }
    }

    @Test
    void rutasDeUsuariosYRolesConservanQuerySinToken() throws Exception {
        for (String path : new String[]{"/usuarios", "/roles", "/usuarios/7/artistas-favoritos?pagina=1",
                "/usuarios/7/recomendaciones/por-compras", "/usuarios/7/recomendaciones?limite=2"}) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("GET " + path + "|", response.body());
        }
    }

    @Test
    void noPublicaElContratoInternoDeComprasEnComun() throws Exception {
        for (String ruta : new String[]{"/interno/recomendaciones/clientes/7",
                "/interno/recomendaciones/clientes/7/historial-valido", "/interno/inventario/disponibilidad",
                "/interno/eventos/recomendaciones/candidatos", "/interno/eventos/recomendaciones/por-ids"}) {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                    + ruta)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode());
        }
    }

    @Test
    void conservaCabecerasDeHistorialNoVerificado() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/usuarios/7/recomendaciones/por-preferencias")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("false", response.headers().firstValue("X-Historial-Verificado").orElseThrow());
        assertEquals("false", response.headers().firstValue("X-Disponibilidad-Verificada").orElseThrow());
        assertEquals("true", response.headers().firstValue("X-Respuesta-Parcial").orElseThrow());
    }

    @Test
    void conserva404DelMicroservicio() throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/usuarios/999"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
    }
}
