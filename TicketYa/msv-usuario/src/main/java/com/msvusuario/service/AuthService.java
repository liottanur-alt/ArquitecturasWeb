package com.msvusuario.service;

import com.msvusuario.dto.*;
import com.msvusuario.entity.*;
import com.msvusuario.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {
    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final String issuer;
    private final long ttl;
    private final String dummyHash;

    public AuthService(UsuarioRepository usuarios, RolRepository roles, PasswordEncoder passwords,
                       JwtEncoder encoder, @Value("${security.jwt.issuer}") String issuer,
                       @Value("${security.jwt.ttl-seconds}") long ttl) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.passwords = passwords;
        this.encoder = encoder;
        this.issuer = issuer;
        this.ttl = ttl;
        this.dummyHash = passwords.encode("cuenta-inexistente-no-autenticar");
    }

    @Transactional
    public UsuarioResponse registrar(RegistroRequest request) {
        if (request == null || !texto(request.nombre(), 100) || !texto(request.email(), 254)
                || !texto(request.dni(), 20) || !texto(request.ciudad(), 100)
                || !request.email().trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || request.password() == null || request.password().length() < 4
                || request.password().isBlank()
                || request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Datos invalidos: password requiere al menos 4 caracteres y hasta 72 bytes");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByEmailIgnoreCase(email) || usuarios.existsByDni(request.dni().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email o DNI ya registrado");
        }
        Rol cliente = roles.findByName(TipoRol.CLIENTE).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Rol CLIENTE no inicializado"));
        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre().trim());
        usuario.setEmail(email);
        usuario.setDni(request.dni().trim());
        usuario.setCiudad(request.ciudad().trim());
        usuario.setRol(cliente);
        usuario.setPasswordHash(passwords.encode(request.password()));
        return UsuarioResponse.from(usuarios.saveAndFlush(usuario));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        if (request == null || request.email() == null || request.password() == null
                || request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas");
        }
        Usuario usuario = usuarios.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        String hash = usuario == null || usuario.getPasswordHash() == null ? dummyHash : usuario.getPasswordHash();
        boolean valido = passwords.matches(request.password(), hash);
        if (usuario == null || usuario.getPasswordHash() == null || !valido) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales invalidas");
        }
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(usuario.getId().toString())
                .issuedAt(ahora).expiresAt(ahora.plusSeconds(ttl))
                .claim("roles", List.of(usuario.getRol().getName().name())).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ttl);
    }

    private boolean texto(String valor, int max) {
        return valor != null && !valor.isBlank() && valor.trim().length() <= max;
    }
}
