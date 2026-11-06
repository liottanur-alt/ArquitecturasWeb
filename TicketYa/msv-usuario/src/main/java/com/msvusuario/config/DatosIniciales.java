package com.msvusuario.config;

import com.msvusuario.entity.*;
import com.msvusuario.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DatosIniciales implements ApplicationRunner {
    private final RolRepository roles;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwords;

    @Value("${app.seed-admin:false}")
    private boolean crearAdmin;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (TipoRol tipo : TipoRol.values()) {
            if (roles.findByName(tipo).isEmpty()) {
                Rol rol = new Rol();
                rol.setName(tipo);
                roles.save(rol);
            }
        }
        // Solo crea la cuenta de prueba una vez; no cambia claves de cuentas existentes.
        if (crearAdmin && !usuarios.existsByEmailIgnoreCase("admin@admin.com")) {
            Usuario admin = new Usuario();
            admin.setNombre("Administrador de prueba");
            admin.setEmail("admin@admin.com");
            admin.setDni("ADMIN-DEMO");
            admin.setCiudad("Tandil");
            admin.setRol(roles.findByName(TipoRol.ADMINISTRADOR).orElseThrow());
            admin.setPasswordHash(passwords.encode("1234"));
            usuarios.save(admin);
        }
    }
}
