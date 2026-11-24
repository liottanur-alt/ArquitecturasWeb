package com.apigateway.config;

import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.RequestMatcher;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecretKey jwtKey(@Value("${security.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET requiere al menos 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, @Value("${security.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        var bearer = new BearerTokenAuthenticationEntryPoint();
        var permisos = new BearerTokenAccessDeniedHandler();
        AuthenticationEntryPoint iniciarSesion = (request, response, error) -> {
            bearer.commence(request, response, error);
            response.sendError(response.getStatus());
        };
        AccessDeniedHandler accesoDenegado = (request, response, error) -> {
            permisos.handle(request, response, error);
            response.sendError(response.getStatus());
        };

        return http.csrf(csrf -> csrf.disable())
                .addFilterAfter(new RegistroHttpFilter(),
                        SecurityContextHolderFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/registro", "/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers(internalRoute()).denyAll()
                        .requestMatchers("/roles", "/roles/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/usuarios").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/usuarios").hasRole("ADMINISTRADOR")
                        .requestMatchers("/usuarios/{id}", "/usuarios/{id}/**").access(this::mismoUsuarioOAdmin)
                        .requestMatchers(HttpMethod.POST, "/api/eventos").hasAnyRole("ORGANIZADOR", "ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/eventos/{id}/suspender").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/eventos/{id}/cancelar").denyAll()
                        .requestMatchers("/api/reportes/eventos/**").hasAnyRole("ORGANIZADOR", "ADMINISTRADOR")
                        .requestMatchers("/api/eventos/{id}/fila", "/api/eventos/{id}/fila/**").hasRole("CLIENTE")
                        .requestMatchers(HttpMethod.PATCH, "/api/eventos/{id}/publicar")
                        .hasAnyRole("ORGANIZADOR", "ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/api/eventos/mis-eventos")
                        .hasAnyRole("ORGANIZADOR", "ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/entradas/qr/**")
                        .hasAnyRole("PERSONAL_ACCESO", "ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PUT, "/solicitudes/{id}/resolver").hasRole("ADMINISTRADOR")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(iniciarSesion)
                        .accessDeniedHandler(accesoDenegado))
                .oauth2ResourceServer(oauth -> oauth.authenticationEntryPoint(iniciarSesion)
                        .accessDeniedHandler(accesoDenegado)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }

    private AuthorizationDecision mismoUsuarioOAdmin(Supplier<? extends Authentication> authentication,
                                                     RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        if (auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMINISTRADOR".equals(a.getAuthority()))) {
            return new AuthorizationDecision(true);
        }
        String id = context.getVariables().get("id");
        return new AuthorizationDecision(id != null && id.equals(auth.getName()));
    }

    private RequestMatcher internalRoute() {
        return request -> {
            for (String segment : request.getRequestURI().split("/")) {
                if ("interno".equalsIgnoreCase(segment)) {
                    return true;
                }
            }
            return false;
        };
    }
}
