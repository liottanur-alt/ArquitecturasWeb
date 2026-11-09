package com.msvusuario;

import com.msvusuario.repository.RolRepository;
import com.msvusuario.entity.TipoRol;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Comprueba que el CRUD sigue disponible al retirar autenticacion y permisos JWT. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class UsuarioSinAuthIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired RolRepository roles;

    @Test void crudYFavoritosFuncionanSinToken() throws Exception {
        Long rol = roles.findByName(TipoRol.CLIENTE).orElseThrow().getId();
        String body = """
            {"nombre":"Ana","email":"sin-auth@example.com","dni":"SIN-AUTH-001","ciudad":"Tandil","idRol":%d}
            """.formatted(rol);
        mvc.perform(get("/roles")).andExpect(status().isOk());
        mvc.perform(get("/usuarios")).andExpect(status().isOk());
        String response = mvc.perform(post("/usuarios").contentType("application/json").content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(response, "$.id");
        String ruta = "/usuarios/" + id;
        mvc.perform(get(ruta)).andExpect(status().isOk());
        mvc.perform(put(ruta).contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(put(ruta + "/artistas-favoritos/123")).andExpect(status().isNoContent());
        mvc.perform(get(ruta + "/artistas-favoritos")).andExpect(status().isOk()).andExpect(jsonPath("$[0]").value(123));
        mvc.perform(delete(ruta + "/artistas-favoritos/123")).andExpect(status().isNoContent());
        mvc.perform(delete(ruta)).andExpect(status().isNoContent());
        mvc.perform(get(ruta)).andExpect(status().isNotFound());
    }

    @Test void rutasDeAutenticacionYaNoEstanRegistradas() throws Exception {
        mvc.perform(post("/auth/login").contentType("application/json").content("{}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/auth/registro").contentType("application/json").content("{}"))
            .andExpect(status().isNotFound());
    }
}
