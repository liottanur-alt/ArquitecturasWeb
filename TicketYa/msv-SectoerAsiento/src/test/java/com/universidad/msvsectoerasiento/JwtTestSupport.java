package com.universidad.msvsectoerasiento;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

final class JwtTestSupport {
    static final String SECRET = "clave-de-prueba-solo-tests-1234567890abcdef";
    static final String ISSUER = "msv-usuario";
    static final String SERVICIO = "msv-SectoerAsiento";

    private JwtTestSupport() {
    }

    static String usuario(String subject, String... roles) {
        return token(ISSUER, subject, null, 1800, roles);
    }

    static String servicio() {
        return servicio(null);
    }

    static String servicio(Object usuarioId) {
        return token(ISSUER, SERVICIO, usuarioId, 300, "SERVICIO_INTERNO");
    }

    static String token(String issuer, String subject, Object usuarioId, long seconds, String... roles) {
        Instant expira = Instant.now().plusSeconds(seconds);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(expira.minusSeconds(1800))
                .expiresAt(expira)
                .claim("roles", List.of(roles));
        if (usuarioId != null) {
            claims.claim("usuarioId", usuarioId);
        }
        return encode(clave(SECRET), claims.build());
    }

    static String conOtroSecreto(String subject, String... roles) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(subject)
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(1800))
                .claim("roles", List.of(roles))
                .build();
        return encode(clave("otro-secreto-de-prueba-1234567890abcdef"), claims);
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    private static SecretKey clave(String secreto) {
        return new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static String encode(SecretKey key, JwtClaimsSet claims) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
