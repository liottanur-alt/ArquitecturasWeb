package com.msvusuario;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/** Base de pruebas de negocio: autentica como administrador sin pasar por el filtro JWT. */
abstract class TestSecuritySupport extends MongoIntegrationSupport {

    @BeforeEach
    void autenticarComoAdministrador() {
        var auth = new UsernamePasswordAuthenticationToken("0", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void limpiarAutenticacion() {
        SecurityContextHolder.clearContext();
    }
}
