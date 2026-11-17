package com.msvusuario;

import com.msvusuario.dto.UsuarioRequest;
import com.msvusuario.entity.TipoEvento;
import com.msvusuario.entity.TipoRol;
import com.msvusuario.repository.RolRepository;
import com.msvusuario.repository.UsuarioRepository;
import com.msvusuario.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PreferenciasIntegrationTests extends MongoIntegrationSupport {
    @Autowired MockMvc mvc;
    @Autowired UsuarioService service;
    @Autowired UsuarioRepository usuarios;
    @Autowired RolRepository roles;
    private Long id;

    @BeforeEach
    void crearUsuario() {
        usuarios.deleteAll();
        Long rol = roles.findByName(TipoRol.CLIENTE).orElseThrow().getId();
        id = service.crear(new UsuarioRequest("Ana", "preferencias@example.com", "PREF-001", "Tandil", rol)).id();
    }

    private String ruta() { return "/usuarios/" + id + "/preferencias"; }

    @Test
    void consultaPreferenciasEIdsLocalesSinModificarElDocumento() throws Exception {
        service.agregarFavorito(id, 900L);
        service.actualizarPreferencias(id, new com.msvusuario.dto.PreferenciasRequest(
                java.util.List.of(TipoEvento.RECITAL), "Rosario"));
        var coleccion = template.getCollection("usuarios");
        var filtro = new org.bson.Document("_id", id);
        var antes = coleccion.find(filtro).first();
        String respuesta = mvc.perform(get(ruta())).andExpect(status().isOk())
                .andExpect(jsonPath("$.tiposEventoPreferidos[0]").value("RECITAL"))
                .andExpect(jsonPath("$.ciudadPreferida").value("Rosario"))
                .andExpect(jsonPath("$.artistasFavoritos[0]").value(900))
                .andExpect(jsonPath("$.nombre").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertEquals(3, tools.jackson.databind.json.JsonMapper.builder().build().readTree(respuesta).size());
        assertEquals(antes, coleccion.find(filtro).first());
    }

    @Test
    void consultaSinPreferenciasDevuelveListasVaciasYCiudadNull() throws Exception {
        String respuesta = mvc.perform(get(ruta())).andExpect(status().isOk())
                .andExpect(jsonPath("$.tiposEventoPreferidos").isEmpty())
                .andExpect(jsonPath("$.artistasFavoritos").isEmpty())
                .andReturn().getResponse().getContentAsString();
        var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(respuesta);
        assertTrue(json.has("ciudadPreferida"));
        assertTrue(json.get("ciudadPreferida").isNull());
    }

    @Test
    void consultaDocumentosAnterioresYUsuarioInexistente() throws Exception {
        template.updateFirst(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("id").is(id)),
                new org.springframework.data.mongodb.core.query.Update().unset("tiposEventoPreferidos")
                        .unset("ciudadPreferida").unset("artistasFavoritos"), com.msvusuario.entity.Usuario.class);
        mvc.perform(get(ruta())).andExpect(status().isOk())
                .andExpect(jsonPath("$.tiposEventoPreferidos").isEmpty())
                .andExpect(jsonPath("$.artistasFavoritos").isEmpty());
        mvc.perform(get("/usuarios/9223372036854775807/preferencias")).andExpect(status().isNotFound());
    }

    @Test
    void vaciarTiposYCiudadNoOcultaLosArtistasFavoritos() throws Exception {
        service.agregarFavorito(id, 900L);
        service.actualizarPreferencias(id, new com.msvusuario.dto.PreferenciasRequest(java.util.List.of(), ""));
        mvc.perform(get(ruta())).andExpect(status().isOk())
                .andExpect(jsonPath("$.tiposEventoPreferidos").isEmpty())
                .andExpect(jsonPath("$.artistasFavoritos[0]").value(900));
    }

    @Test
    void guardaDeduplicaModificaYConservaPerfilYFavoritos() throws Exception {
        var anterior = usuarios.findById(id).orElseThrow();
        service.agregarFavorito(id, 900L);
        mvc.perform(put(ruta()).contentType("application/json").content("""
                {"tiposEventoPreferidos":["RECITAL","TEATRO","RECITAL"],"ciudadPreferida":"  Mar del Plata  "}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tiposEventoPreferidos.length()").value(2))
                .andExpect(jsonPath("$.ciudadPreferida").value("Mar del Plata"))
                .andExpect(jsonPath("$.ciudad").value("Tandil"));
        var guardado = usuarios.findById(id).orElseThrow();
        assertEquals(Set.of(TipoEvento.RECITAL, TipoEvento.TEATRO), guardado.getTiposEventoPreferidos());
        assertEquals(anterior.getNombre(), guardado.getNombre());
        assertEquals(anterior.getEmail(), guardado.getEmail());
        assertEquals(anterior.getDni(), guardado.getDni());
        assertEquals(anterior.getCiudad(), guardado.getCiudad());
        assertEquals(anterior.getRol().getId(), guardado.getRol().getId());
        assertEquals(Set.of(900L), guardado.getArtistasFavoritos());
        mvc.perform(put(ruta()).contentType("application/json").content("""
                {"tiposEventoPreferidos":["FESTIVAL"],"ciudadPreferida":"Rosario"}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tiposEventoPreferidos[0]").value("FESTIVAL"));
        mvc.perform(get("/usuarios/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ciudadPreferida").value("Rosario"));
        assertEquals(Set.of(900L), usuarios.findById(id).orElseThrow().getArtistasFavoritos());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"tiposEventoPreferidos\":[],\"ciudadPreferida\":\"\"}",
            "{\"tiposEventoPreferidos\":null,\"ciudadPreferida\":null}"})
    void permiteVaciarPreferencias(String body) throws Exception {
        mvc.perform(put(ruta()).contentType("application/json").content("""
                {"tiposEventoPreferidos":["RECITAL"],"ciudadPreferida":"Rosario"}
                """)) .andExpect(status().isOk());
        mvc.perform(put(ruta()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tiposEventoPreferidos").isEmpty())
                .andExpect(jsonPath("$.ciudadPreferida").value(""));
        assertTrue(usuarios.findById(id).orElseThrow().getTiposEventoPreferidos().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"tiposEventoPreferidos\":[\"DESCONOCIDO\"]}",
            "{\"tiposEventoPreferidos\":[null]}",
            "{\"tiposEventoPreferidos\":[0]}",
            "{\"tiposEventoPreferidos\":\"RECITAL\"}",
            "{\"ciudadPreferida\":123}", "{\"ciudadPreferida\":true}",
            "{\"ciudadPreferida\":[]}", "{\"ciudadPreferida\":{}}", "null", "{"
    })
    void rechazaDatosInvalidosSinModificarUsuario(String body) throws Exception {
        var anterior = usuarios.findById(id).orElseThrow();
        mvc.perform(put(ruta()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        var despues = usuarios.findById(id).orElseThrow();
        assertEquals(anterior.getVersion(), despues.getVersion());
        assertTrue(despues.getTiposEventoPreferidos().isEmpty());
        assertEquals("", despues.getCiudadPreferida());
    }

    @Test
    void ciudadDemasiadoLargaEs400YUsuarioInexistenteEs404() throws Exception {
        mvc.perform(put(ruta()).contentType("application/json")
                .content("{\"ciudadPreferida\":\"" + "a".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/usuarios/9223372036854775807/preferencias").contentType("application/json")
                .content("{}")) .andExpect(status().isNotFound());
    }

    @Test
    void aceptaTodosLosTiposDelCatalogoYLaCiudadLimite() throws Exception {
        mvc.perform(put(ruta()).contentType("application/json").content("""
                {"tiposEventoPreferidos":["RECITAL","TEATRO","DEPORTE","CONFERENCIA","FESTIVAL","OTRO"],
                 "ciudadPreferida":"%s"}
                """.formatted("a".repeat(100))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tiposEventoPreferidos.length()").value(6));
    }

    @Test
    void usuariosAnterioresSinCamposNuevosSeLeenConPreferenciasVacias() throws Exception {
        var mongo = org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("id").is(id));
        // Simula documentos creados antes de incorporar las preferencias.
        template.updateFirst(mongo, new org.springframework.data.mongodb.core.query.Update()
                .unset("tiposEventoPreferidos").unset("ciudadPreferida"), com.msvusuario.entity.Usuario.class);
        mvc.perform(get("/usuarios/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.tiposEventoPreferidos").isEmpty())
                .andExpect(jsonPath("$.ciudadPreferida").value(""));
    }

    @Autowired org.springframework.data.mongodb.core.MongoTemplate template;
}
