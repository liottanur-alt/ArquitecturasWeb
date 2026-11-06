package com.msvusuario;

import com.jayway.jsonpath.JsonPath;
import com.msvusuario.config.DatosIniciales;
import com.msvusuario.entity.TipoRol;
import com.msvusuario.entity.Usuario;
import com.msvusuario.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.seed-admin=true",
        "spring.datasource.url=jdbc:h2:mem:auth-test;MODE=MySQL;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;
    @Autowired DatosIniciales datos;

    @Test
    void registroGuardaHashYAsignaClienteAunqueSeEnvieOtroRol() throws Exception {
        registrar();
        Usuario usuario = usuarios.findByEmailIgnoreCase("ana@example.com").orElseThrow();
        assertEquals(TipoRol.CLIENTE, usuario.getRol().getName());
        assertNotEquals("1234", usuario.getPasswordHash());
        assertTrue(passwords.matches("1234", usuario.getPasswordHash()));
    }

    @Test
    void registroDuplicadoDevuelveConflicto() throws Exception {
        registrar();
        mvc.perform(post("/auth/registro").contentType(MediaType.APPLICATION_JSON).content(registroJson()))
                .andExpect(status().isConflict());
    }

    @Test
    void registroInvalidoNoCreaCuenta() throws Exception {
        mvc.perform(post("/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(registroJson().replace("1234", "1")))
                .andExpect(status().isBadRequest());
        assertFalse(usuarios.existsByEmailIgnoreCase("ana@example.com"));
    }

    @Test
    void loginEmiteJwtConIdRolYVencimientoSinDatosSensibles() throws Exception {
        registrar();
        String token = login("ana@example.com", "1234");
        Jwt jwt = decoder.decode(token);
        assertEquals(usuarios.findByEmailIgnoreCase("ana@example.com").orElseThrow().getId().toString(), jwt.getSubject());
        assertEquals(List.of("CLIENTE"), jwt.getClaimAsStringList("roles"));
        assertEquals(1800, jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond());
        assertFalse(jwt.hasClaim("password"));
        assertFalse(jwt.hasClaim("dni"));
    }

    @Test
    void passwordIncorrectoYUsuarioInexistenteDan401() throws Exception {
        registrar();
        for (String email : List.of("ana@example.com", "nadie@example.com")) {
            mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"incorrecta\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void sinTokenOTokenInvalidoNoPermiteAcceso() throws Exception {
        mvc.perform(get("/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/usuarios").header("Authorization", "Bearer invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenVencidoOEmisorIncorrectoNoPermiteAcceso() throws Exception {
        Instant ahora = Instant.now();
        for (JwtClaimsSet claims : List.of(
                JwtClaimsSet.builder().issuer("msv-usuario").subject("1")
                        .issuedAt(ahora.minusSeconds(600)).expiresAt(ahora.minusSeconds(300)).build(),
                JwtClaimsSet.builder().issuer("otro-emisor").subject("1")
                        .issuedAt(ahora).expiresAt(ahora.plusSeconds(600)).build())) {
            String token = encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            mvc.perform(get("/usuarios").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void clienteAccedeASuPerfilPeroNoAOtrosNiAdministraRoles() throws Exception {
        registrar();
        Long id = usuarios.findByEmailIgnoreCase("ana@example.com").orElseThrow().getId();
        Long admin = usuarios.findByEmailIgnoreCase("admin@admin.com").orElseThrow().getId();
        String bearer = "Bearer " + login("ana@example.com", "1234");
        mvc.perform(get("/usuarios/" + id).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/usuarios/" + admin).header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/usuarios/" + admin + "/artistas-favoritos").header("Authorization", bearer))
                .andExpect(status().isForbidden());
        mvc.perform(get("/roles").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/usuarios").header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    @Test
    void clientePuedeEditarPerfilPeroNoAsignarseAdministrador() throws Exception {
        registrar();
        Usuario usuario = usuarios.findByEmailIgnoreCase("ana@example.com").orElseThrow();
        Usuario admin = usuarios.findByEmailIgnoreCase("admin@admin.com").orElseThrow();
        String bearer = "Bearer " + login("ana@example.com", "1234");
        String body = "{\"nombre\":\"Ana Editada\",\"email\":\"ana@example.com\",\"dni\":\"12345678\",\"ciudad\":\"Tandil\",\"idRol\":%d}";
        mvc.perform(put("/usuarios/" + usuario.getId()).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body.formatted(usuario.getRol().getId())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Ana Editada"));
        mvc.perform(put("/usuarios/" + usuario.getId()).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body.formatted(admin.getRol().getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDePruebaPuedeIniciarSesionYNoSeDuplicaAlReiniciar() throws Exception {
        String hash = usuarios.findByEmailIgnoreCase("admin@admin.com").orElseThrow().getPasswordHash();
        datos.run(new DefaultApplicationArguments(new String[0]));
        assertEquals(hash, usuarios.findByEmailIgnoreCase("admin@admin.com").orElseThrow().getPasswordHash());
        String token = login("admin@admin.com", "1234");
        mvc.perform(get("/usuarios").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/roles").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void cuentaAntiguaSinPasswordNoPuedeIniciarSesion() throws Exception {
        registrar();
        Usuario usuario = usuarios.findByEmailIgnoreCase("ana@example.com").orElseThrow();
        usuario.setPasswordHash(null);
        usuarios.saveAndFlush(usuario);
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ana@example.com\",\"password\":\"1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    private void registrar() throws Exception {
        mvc.perform(post("/auth/registro").contentType(MediaType.APPLICATION_JSON).content(registroJson()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    private String registroJson() {
        return """
                {"nombre":"Ana","email":"ana@example.com","dni":"12345678",
                 "ciudad":"Tandil","password":"1234","idRol":999,"rol":"ADMINISTRADOR"}
                """;
    }

    private String login(String email, String password) throws Exception {
        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }
}
