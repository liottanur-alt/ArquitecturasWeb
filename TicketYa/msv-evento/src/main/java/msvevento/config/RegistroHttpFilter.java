package msvevento.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import msvevento.client.NotificacionEventoClient;
import org.springframework.beans.factory.ObjectProvider;
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

    private final ObjectProvider<NotificacionEventoClient> auditoria;

    public RegistroHttpFilter(ObjectProvider<NotificacionEventoClient> auditoria) {
        this.auditoria = auditoria;
    }

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
            if ("PATCH".equals(request.getMethod()) && ruta.matches("/api/eventos/[^/]+/(cancelar|suspender)")) {
                try {
                    auditoria.getObject().registrarLog(new NotificacionEventoClient.Log(
                            "eventos", estado >= 400 ? "WARN" : "INFO", mensaje, id));
                } catch (RuntimeException error) {
                    log.warn("No se pudo centralizar el registro de la operación {}", id);
                }
            }
            MDC.remove("correlationId");
        }
    }
}
