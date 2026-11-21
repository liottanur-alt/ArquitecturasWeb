package com.msvusuario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Comprueba que las rutas de usuarios y roles ya no estan disponibles sin token. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class UsuarioSinAuthIntegrationTests extends MongoIntegrationSupport {
    @Autowired MockMvc mvc;

    @Test
    void rutasDeUsuariosYRolesRequierenToken() throws Exception {
        mvc.perform(get("/roles")).andExpect(status().isUnauthorized());
        mvc.perform(get("/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/usuarios/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/usuarios/1/preferencias")).andExpect(status().isUnauthorized());
        mvc.perform(get("/usuarios/1/artistas-favoritos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/usuarios/1/recomendaciones")).andExpect(status().isUnauthorized());
        mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/usuarios/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void rutasDeAutenticacionYDocumentacionSiguenSiendoPublicas() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nadie@example.com\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/registro").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/openapi.json")).andExpect(status().isOk());
    }
}
