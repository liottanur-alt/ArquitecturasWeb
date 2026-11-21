package com.universidad.msvsectoerasiento.config;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

public class ServiceTokenProvider {
    private final JwtEncoder encoder;
    private final String issuer;
    private final String serviceName;

    public ServiceTokenProvider(JwtEncoder encoder, String issuer, String serviceName) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.serviceName = serviceName;
    }

    public String token() {
        Instant emitido = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(serviceName)
                .issuedAt(emitido)
                .expiresAt(emitido.plusSeconds(300))
                .claim("roles", List.of("SERVICIO_INTERNO"));
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt && authentication.isAuthenticated()) {
            try {
                claims.claim("usuarioId", Long.valueOf(jwt.getName()));
            } catch (NumberFormatException ignorado) {
            }
        }
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }
}
