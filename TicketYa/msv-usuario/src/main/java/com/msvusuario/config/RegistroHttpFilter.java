package com.msvusuario.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

public class RegistroHttpFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RegistroHttpFilter.class);
    private static final String HEADER = "X-Correlation-Id";
    private static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recibido = request.getHeader(HEADER);
        String id = recibido != null && ID_VALIDO.matcher(recibido).matches() ? recibido : UUID.randomUUID().toString();
        String ruta = request.getRequestURI().replaceFirst("(/entradas/qr/)[^/]+", "$1{qr}");
        long inicio = System.nanoTime();
        boolean termino = false;
        response.setHeader(HEADER, id);
        MDC.put("correlationId", id);
        try {
            chain.doFilter(request, response);
            termino = true;
        } finally {
            var autenticacion = SecurityContextHolder.getContext().getAuthentication();
            String usuario = autenticacion instanceof JwtAuthenticationToken jwt ? jwt.getName() : "anonimo";
            int estado = termino ? response.getStatus() : 500;
            long duracion = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
            String mensaje = "%s %s usuario=%s status=%d duracionMs=%d"
                    .formatted(request.getMethod(), ruta, usuario, estado, duracion);
            log.info("HTTP {} correlacion={}", mensaje, id);
            MDC.remove("correlationId");
        }
    }
}
