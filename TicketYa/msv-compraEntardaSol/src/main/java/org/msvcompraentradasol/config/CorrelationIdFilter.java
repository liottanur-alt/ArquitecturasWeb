package org.msvcompraentradasol.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.beans.factory.ObjectProvider;
import org.msvcompraentradasol.feigns.NotificacionCompraClient;
import org.springframework.web.filter.OncePerRequestFilter;

public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private final ObjectProvider<NotificacionCompraClient> auditoria;

    public CorrelationIdFilter(ObjectProvider<NotificacionCompraClient> auditoria) {
        this.auditoria = auditoria;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestedId = request.getHeader(HEADER);
        String correlationId = requestedId != null && SAFE_ID.matcher(requestedId).matches()
                ? requestedId
                : UUID.randomUUID().toString();

        MDC.put("correlationId", correlationId);
        response.setHeader(HEADER, correlationId);
        long inicio = System.nanoTime();
        String ruta = request.getRequestURI().replaceFirst("(/entradas/qr/)[^/]+", "$1{qr}");
        boolean termino = false;
        try {
            filterChain.doFilter(request, response);
            termino = true;
        } finally {
            var autenticacion = SecurityContextHolder.getContext().getAuthentication();
            String usuario = autenticacion instanceof JwtAuthenticationToken jwt ? jwt.getName() : "anonimo";
            int estado = termino ? response.getStatus() : 500;
            long duracion = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
            String mensaje = "%s %s usuario=%s status=%d duracionMs=%d"
                    .formatted(request.getMethod(), ruta, usuario, estado, duracion);
            log.info("HTTP {}", mensaje);
            if (esOperacionCritica(request.getMethod(), ruta)) {
                try {
                    auditoria.getObject().registrarLog(new NotificacionCompraClient.Log(
                            "compras", estado >= 400 ? "WARN" : "INFO", mensaje, correlationId));
                } catch (RuntimeException error) {
                    log.warn("No se pudo centralizar el registro de la operación");
                }
            }
            MDC.remove("correlationId");
        }
    }

    private boolean esOperacionCritica(String metodo, String ruta) {
        return ("POST".equals(metodo) && (ruta.equals("/compras") || ruta.equals("/solicitudes")
                || ruta.matches("/compras/eventos/[^/]+/cancelacion")))
                || ("PUT".equals(metodo) && ruta.matches("/compras/[^/]+/pago|/solicitudes/[^/]+/resolver"))
                || ("PATCH".equals(metodo) && ruta.startsWith("/entradas/") && ruta.endsWith("/uso"));
    }
}
