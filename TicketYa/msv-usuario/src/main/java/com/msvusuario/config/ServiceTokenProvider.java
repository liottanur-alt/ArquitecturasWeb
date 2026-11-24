package com.msvusuario.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ServiceTokenProvider {
    private static final long TTL_SECONDS = 300;

    private final JwtEncoder encoder;
    private final String issuer;
    private final String service;

    public ServiceTokenProvider(JwtEncoder encoder, @Value("${security.jwt.issuer}") String issuer,
                                @Value("${app.service-name}") String service) {
        this.encoder = encoder;
        this.issuer = issuer;
        this.service = service;
    }

    public String token() {
        Instant ahora = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(service)
                .issuedAt(ahora)
                .expiresAt(ahora.plusSeconds(TTL_SECONDS))
                .claim("roles", List.of("SERVICIO_INTERNO"));
        Long usuarioId = usuarioIdAutenticado();
        if (usuarioId != null) {
            claims.claim("usuarioId", usuarioId);
        }
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    private Long usuarioIdAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth) || !jwtAuth.isAuthenticated()) {
            return null;
        }
        try {
            return Long.valueOf(jwtAuth.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
