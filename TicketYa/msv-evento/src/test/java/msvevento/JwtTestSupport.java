package msvevento;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class JwtTestSupport {

    static final String SECRET = "clave-de-prueba-solo-tests-1234567890abcdef";
    static final String ISSUER = "msv-usuario";
    private static final SecretKey KEY = new SecretKeySpec(
            SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private static final JwtEncoder ENCODER = new NimbusJwtEncoder(new ImmutableSecret<>(KEY));

    private JwtTestSupport() {
    }

    static String token(String sub, String... roles) {
        return token(ISSUER, sub, 1800, Map.of(), roles);
    }

    static String token(String sub, long seconds, Map<String, Object> claims, String... roles) {
        return token(ISSUER, sub, seconds, claims, roles);
    }

    static String token(String issuer, String sub, long seconds, Map<String, Object> claims, String... roles) {
        Instant ahora = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder().issuer(issuer).subject(sub)
                .issuedAt(ahora).expiresAt(ahora.plusSeconds(seconds))
                .claim("roles", List.of(roles));
        claims.forEach(builder::claim);
        return ENCODER.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), builder.build())).getTokenValue();
    }

    static String serviceToken() {
        return token("msv-evento", 300, Map.of(), "SERVICIO_INTERNO");
    }

    static String serviceToken(long usuarioId) {
        return token("msv-evento", 300, Map.of("usuarioId", usuarioId), "SERVICIO_INTERNO");
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    static Jwt decode(String token) {
        return NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build().decode(token);
    }
}
