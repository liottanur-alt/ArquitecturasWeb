package msvevento.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reemplaza la identidad enviada por el cliente con la verificada en el JWT. */
@Component
@Order(0)
public class IdentityHeaderFilter extends OncePerRequestFilter {
    public static final String ORGANIZADOR_HEADER = "X-Organizador-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) {
            chain.doFilter(new IdentidadVerificadaRequest(request, jwt.getName()), response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static final class IdentidadVerificadaRequest extends HttpServletRequestWrapper {
        private final String id;

        private IdentidadVerificadaRequest(HttpServletRequest request, String id) {
            super(request);
            this.id = id;
        }

        @Override
        public String getHeader(String name) {
            if (ORGANIZADOR_HEADER.equalsIgnoreCase(name)) {
                return id;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (ORGANIZADOR_HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(id));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>(Collections.list(super.getHeaderNames()));
            names.add(ORGANIZADOR_HEADER);
            return Collections.enumeration(names);
        }
    }
}
