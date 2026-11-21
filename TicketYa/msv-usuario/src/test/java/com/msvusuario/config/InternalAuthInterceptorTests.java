package com.msvusuario.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalAuthInterceptorTests {
    private static final String SECRETO = "clave-de-prueba-solo-tests-1234567890abcdef";

    private final SecretKey key = new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final ServiceTokenProvider provider = new ServiceTokenProvider(
            new NimbusJwtEncoder(new ImmutableSecret<>(key)), "msv-usuario", "msv-usuario");
    private final InternalAuthInterceptor interceptor = new InternalAuthInterceptor(provider);
    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256).build();

    @AfterEach
    void limpiarAutenticacion() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void agregaTokenDeServicioConUsuarioIdDelJwtDelUsuario() {
        autenticar("123");
        RequestTemplate template = new RequestTemplate();
        template.header(HttpHeaders.AUTHORIZATION, "Bearer token-anterior");

        interceptor.apply(template);

        Jwt token = decodificar(template);
        assertEquals("msv-usuario", token.getClaimAsString("iss"));
        assertEquals("msv-usuario", token.getSubject());
        assertEquals(List.of("SERVICIO_INTERNO"), token.getClaimAsStringList("roles"));
        assertEquals(123L, ((Number) token.getClaim("usuarioId")).longValue());
        assertEquals(Duration.ofSeconds(300), Duration.between(token.getIssuedAt(), token.getExpiresAt()));
    }

    @Test
    void sinUsuarioNoIncluyeUsuarioId() {
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        Jwt token = decodificar(template);
        assertFalse(token.getClaims().containsKey("usuarioId"));
    }

    @Test
    void subjectDeUsuarioNoNumericoOmiteUsuarioId() {
        autenticar("usuario-no-numerico");
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        Jwt token = decodificar(template);
        assertFalse(token.getClaims().containsKey("usuarioId"));
    }

    private void autenticar(String subject) {
        Instant ahora = Instant.now();
        Jwt jwt = Jwt.withTokenValue("jwt-usuario")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(60))
                .claim("roles", List.of("CLIENTE"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));
    }

    private Jwt decodificar(RequestTemplate template) {
        Collection<String> headers = template.headers().get(HttpHeaders.AUTHORIZATION);
        assertNotNull(headers);
        assertEquals(1, headers.size());
        String valor = headers.iterator().next();
        assertTrue(valor.startsWith("Bearer "));
        return decoder.decode(valor.substring("Bearer ".length()));
    }
}
