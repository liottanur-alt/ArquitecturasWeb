package org.msvcompraentradasol;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public final class JwtTestSupport {

    public static final String SECRET = "clave-de-prueba-solo-tests-1234567890abcdef";
    public static final String ISSUER = "msv-usuario";
    private static final SecretKey KEY = new SecretKeySpec(
            SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private static final JwtEncoder ENCODER = new NimbusJwtEncoder(new ImmutableSecret<>(KEY));

    private JwtTestSupport() {
    }

    public static String token(String sub, String... roles) {
        return token(sub, 1800, Map.of(), roles);
    }

    public static String token(String sub, long seconds, Map<String, Object> claims, String... roles) {
        Instant ahora = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder().issuer(ISSUER).subject(sub)
                .issuedAt(ahora).expiresAt(ahora.plusSeconds(seconds))
                .claim("roles", List.of(roles));
        claims.forEach(builder::claim);
        return ENCODER.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), builder.build())).getTokenValue();
    }

    public static String serviceToken(long usuarioId) {
        return token("msv-compraEntardaSol", 300, Map.of("usuarioId", usuarioId), "SERVICIO_INTERNO");
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    public static Jwt decode(String token) {
        return NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build().decode(token);
    }
}
