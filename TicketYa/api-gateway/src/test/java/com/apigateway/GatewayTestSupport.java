package com.apigateway;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@ActiveProfiles("test")
abstract class GatewayTestSupport {
    @Autowired
    private SecretKey jwtKey;

    protected String token(String sub, String... roles) {
        return token("msv-usuario", sub, 1800, roles);
    }

    protected String token(String issuer, String sub, long seconds, String... roles) {
        Instant expira = Instant.now().plusSeconds(seconds);
        Instant emitido = expira.minusSeconds(1800);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(sub)
                .issuedAt(emitido).expiresAt(expira)
                .claim("roles", List.of(roles)).build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    protected String tokenConOtroSecreto(String sub, String... roles) {
        SecretKey otra = new SecretKeySpec(
                "otro-secreto-de-prueba-1234567890abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("msv-usuario").subject(sub)
                .issuedAt(ahora).expiresAt(ahora.plusSeconds(1800))
                .claim("roles", List.of(roles)).build();
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(otra));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }
}
