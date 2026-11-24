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
class GatewaySecurityTests extends GatewayTestSupport {
    private static final HttpServer backend = backend();
    @Value("${local.server.port}") private int port;
    private final HttpClient client = HttpClient.newHttpClient();

    private static HttpServer backend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String organizador = exchange.getRequestHeaders().getFirst("X-Organizador-Id");
                String body = exchange.getRequestMethod() + " " + exchange.getRequestURI() + "|" + organizador;
                byte[] response = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            });
            server.start();
            return server;
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String url = "http://127.0.0.1:" + backend.getAddress().getPort();
        registry.add("msvc.usuarios.url", () -> url);
        registry.add("msvc.eventos.url", () -> url);
        registry.add("msvc.sectores.url", () -> url);
        registry.add("msvc.compras.url", () -> url);
    }

    @AfterAll
    static void stop() { backend.stop(0); }

    @Test
    void rutasPublicasDeAuthYHealthNoRequierenToken() throws Exception {
        assertEquals(200, call("POST", "/auth/login", null, "{}", null).statusCode());
        assertEquals(200, call("POST", "/auth/registro", null, "{}", null).statusCode());
        assertEquals(200, call("GET", "/actuator/health", null, null, null).statusCode());
    }

    @Test
    void tokenAusenteInvalidoVencidoOReloIncorrectoNoPermiteAcceso() throws Exception {
        var sinSesion = call("GET", "/compras/1", null, null, null);
        assertEquals(401, sinSesion.statusCode());
        assertTrue(sinSesion.body().contains("Iniciá sesión"));
        assertTrue(sinSesion.headers().firstValue("WWW-Authenticate").isPresent());
        assertEquals(401, call("GET", "/compras/1", "Bearer invalido", null, null).statusCode());
        assertEquals(401, call("GET", "/compras/1", bearer(token("msv-usuario", "7", -60, "CLIENTE")),
                null, null).statusCode());
        assertEquals(401, call("GET", "/compras/1", bearer(token("otro-emisor", "7", 1800, "CLIENTE")),
                null, null).statusCode());
        assertEquals(401, call("GET", "/compras/1", bearer(tokenConOtroSecreto("7", "CLIENTE")),
                null, null).statusCode());
    }

    @Test
    void rolesYListadoDeUsuariosRequierenAdministrador() throws Exception {
        String cliente = bearer(token("7", "CLIENTE"));
        String admin = bearer(token("1", "ADMINISTRADOR"));
        for (String path : new String[]{"/roles", "/usuarios"}) {
            var rechazado = call("GET", path, cliente, null, null);
            assertEquals(403, rechazado.statusCode());
            assertTrue(rechazado.body().contains("Tu cuenta no tiene permiso"));
            assertEquals(200, call("GET", path, admin, null, null).statusCode());
        }
        assertEquals(403, call("POST", "/usuarios", cliente, "{}", null).statusCode());
        assertEquals(200, call("POST", "/usuarios", admin, "{}", null).statusCode());
    }

    @Test
    void recursosDeUsuarioSoloParaElDuenoOAdministrador() throws Exception {
        String dueno = bearer(token("7", "CLIENTE"));
        String otro = bearer(token("8", "CLIENTE"));
        String admin = bearer(token("1", "ADMINISTRADOR"));
        for (String path : new String[]{"/usuarios/7", "/usuarios/7/preferencias",
                "/usuarios/7/recomendaciones"}) {
            assertEquals(200, call("GET", path, dueno, null, null).statusCode());
            assertEquals(403, call("GET", path, otro, null, null).statusCode());
            assertEquals(200, call("GET", path, admin, null, null).statusCode());
        }
        assertEquals(200, call("PUT", "/usuarios/7", dueno, "{}", null).statusCode());
        assertEquals(403, call("PUT", "/usuarios/7", otro, "{}", null).statusCode());
    }

    @Test
    void rutasInternasSeBloqueanInclusoConTokenDeAdministrador() throws Exception {
        String admin = bearer(token("1", "ADMINISTRADOR"));
        for (String path : new String[]{"/api/eventos/interno/1",
                "/api/eventos/interno/1/disponibilidad", "/interno/recomendaciones/clientes/7"}) {
            assertEquals(403, call("GET", path, admin, null, null).statusCode());
        }
    }

    @Test
    void eventosSoloOrganizadorOAdminYIdentidadDelHeaderVieneDelJwt() throws Exception {
        String cliente = bearer(token("7", "CLIENTE"));
        String organizador = bearer(token("77", "ORGANIZADOR"));
        String admin = bearer(token("1", "ADMINISTRADOR"));
        assertEquals(403, call("POST", "/api/eventos", cliente, "{}", null).statusCode());
        var creado = call("POST", "/api/eventos", organizador, "{}", "999");
        assertEquals(200, creado.statusCode());
        assertEquals("POST /api/eventos|77", creado.body());
        assertEquals(200, call("POST", "/api/eventos", admin, "{}", null).statusCode());
        assertEquals(200, call("PATCH", "/api/eventos/1/publicar", organizador, null, null).statusCode());
        assertEquals(200, call("GET", "/api/eventos/mis-eventos", organizador, null, null).statusCode());
        assertEquals(403, call("GET", "/api/eventos/mis-eventos", cliente, null, null).statusCode());
    }

    @Test
    void escaneoDeQrSoloParaPersonalDeAccesoOAdministrador() throws Exception {
        String cliente = bearer(token("7", "CLIENTE"));
        String personal = bearer(token("9", "PERSONAL_ACCESO"));
        String admin = bearer(token("1", "ADMINISTRADOR"));
        assertEquals(403, call("GET", "/entradas/qr/abc", cliente, null, null).statusCode());
        assertEquals(200, call("GET", "/entradas/qr/abc", personal, null, null).statusCode());
        assertEquals(200, call("GET", "/entradas/qr/abc", admin, null, null).statusCode());
    }

    @Test
    void resolverSolicitudesRequiereAdministrador() throws Exception {
        String cliente = bearer(token("7", "CLIENTE"));
        String admin = bearer(token("1", "ADMINISTRADOR"));
        assertEquals(403, call("PUT", "/solicitudes/1/resolver", cliente, "{}", null).statusCode());
        assertEquals(200, call("PUT", "/solicitudes/1/resolver", admin, "{}", null).statusCode());
    }

    private HttpResponse<String> call(String method, String path, String authorization, String body,
                                      String organizador) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        if (organizador != null) {
            builder.header("X-Organizador-Id", organizador);
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
