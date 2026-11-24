package org.msvcompraentradasol.config;

import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

public class ServiceTokenProvider {

    private static final long VIGENCIA_SEGUNDOS = 300;

    private final JwtEncoder encoder;
    private final String issuer;
    private final String serviceName;

    public ServiceTokenProvider(JwtEncoder encoder, String issuer, String serviceName) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.serviceName = serviceName;
    }

    public String token() {
        Instant ahora = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(serviceName)
                .claim("roles", List.of("SERVICIO_INTERNO"))
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(VIGENCIA_SEGUNDOS));

        Long usuarioId = SecurityConfig.authenticatedUserIdOrNull();
        if (usuarioId != null) {
            claims.claim("usuarioId", usuarioId);
        }

        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()
        )).getTokenValue();
    }
}
