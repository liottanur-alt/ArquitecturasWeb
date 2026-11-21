package msvevento.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

public class ServiceTokenProvider {

    private final JwtEncoder encoder;
    private final String issuer;
    private final String service;

    public ServiceTokenProvider(JwtEncoder encoder, String issuer, String service) {
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
                .expiresAt(ahora.plusSeconds(300))
                .claim("roles", List.of("SERVICIO_INTERNO"));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) {
            Long usuarioId = numero(jwt.getName());
            if (usuarioId != null) {
                claims.claim("usuarioId", usuarioId);
            }
        }

        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    private Long numero(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return Long.valueOf(valor);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
