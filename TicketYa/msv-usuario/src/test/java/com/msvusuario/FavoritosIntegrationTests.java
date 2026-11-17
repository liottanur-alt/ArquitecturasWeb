package com.msvusuario;

import com.msvusuario.client.ArtistaClientFeign;
import com.msvusuario.dto.ArtistaResponse;
import com.msvusuario.dto.PreferenciasRequest;
import com.msvusuario.dto.UsuarioRequest;
import com.msvusuario.entity.TipoEvento;
import com.msvusuario.entity.TipoRol;
import com.msvusuario.repository.RolRepository;
import com.msvusuario.repository.UsuarioRepository;
import com.msvusuario.service.UsuarioService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FavoritosIntegrationTests extends MongoIntegrationSupport {
    @Autowired MockMvc mvc;
    @Autowired UsuarioService service;
    @Autowired UsuarioRepository usuarios;
    @Autowired RolRepository roles;
    @Autowired MongoTemplate mongo;
    @MockitoBean ArtistaClientFeign artistas;
    private Long id;

    @BeforeEach
    void crearPerfilConPreferencias() {
        usuarios.deleteAll();
        var rol = roles.findByName(TipoRol.CLIENTE).orElseThrow().getId();
        id = service.crear(new UsuarioRequest("Ana", "hu3@example.com", "HU3-001", "Tandil", rol)).id();
        service.actualizarPreferencias(id, new PreferenciasRequest(
                List.of(TipoEvento.RECITAL, TipoEvento.FESTIVAL), "Rosario"));
    }

    private String ruta() { return "/usuarios/" + id + "/artistas-favoritos"; }

    private Document documento() {
        return mongo.getCollection("usuarios").find(new Document("_id", id)).first();
    }

    private void conservaPerfilYPreferencias(Document anterior) {
        var esperado = new Document(anterior);
        var actual = documento();
        // Solo pueden cambiar el array de favoritos y la version de concurrencia.
        for (String campo : List.of("artistasFavoritos", "version")) {
            esperado.remove(campo);
            actual.remove(campo);
        }
        assertEquals(esperado, actual);
    }

    @Test
    void agregarDosVecesNoDuplicaYConservaLasOtrasPreferencias() throws Exception {
        var anterior = documento();
        for (int i = 0; i < 2; i++) {
            mvc.perform(put(ruta() + "/900")).andExpect(status().isNoContent())
                    .andExpect(content().string(""));
            assertEquals(Set.of(900L), usuarios.findById(id).orElseThrow().getArtistasFavoritos());
            conservaPerfilYPreferencias(anterior);
        }
        verifyNoInteractions(artistas);
    }

    @Test
    void quitarIdAusenteNoDaErrorNiQuitaLosOtrosFavoritos() throws Exception {
        service.agregarFavorito(id, 900L);
        var anterior = documento();
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete(ruta() + "/901")).andExpect(status().isNoContent());
            assertEquals(Set.of(900L), usuarios.findById(id).orElseThrow().getArtistasFavoritos());
            conservaPerfilYPreferencias(anterior);
        }
        verifyNoInteractions(artistas);
    }

    @Test
    void quitarRegistradoConservaPerfilTiposYCiudadPreferida() throws Exception {
        service.agregarFavorito(id, 900L);
        var anterior = documento();
        mvc.perform(delete(ruta() + "/900")).andExpect(status().isNoContent());
        assertTrue(usuarios.findById(id).orElseThrow().getArtistasFavoritos().isEmpty());
        conservaPerfilYPreferencias(anterior);
        verifyNoInteractions(artistas);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void idsNoPositivosDevuelven400EnAgregarYQuitarSinCambios(long artista) throws Exception {
        var anterior = documento();
        mvc.perform(put(ruta() + "/" + artista)).andExpect(status().isBadRequest());
        mvc.perform(delete(ruta() + "/" + artista)).andExpect(status().isBadRequest());
        assertEquals(anterior, documento());
        verifyNoInteractions(artistas);
    }

    @Test
    void usuarioInexistenteDevuelve404EnLasTresOperacionesSinCrearlo() throws Exception {
        String rutaInexistente = "/usuarios/9223372036854775807/artistas-favoritos";
        long cantidad = usuarios.count();
        mvc.perform(put(rutaInexistente + "/900")).andExpect(status().isNotFound());
        mvc.perform(delete(rutaInexistente + "/900")).andExpect(status().isNotFound());
        mvc.perform(get(rutaInexistente)).andExpect(status().isNotFound());
        assertEquals(cantidad, usuarios.count());
        verifyNoInteractions(artistas);
    }

    @Test
    void detallesDevuelvenNombreYGeneroDelClienteFeignSinEscribir() throws Exception {
        service.agregarFavorito(id, 900L);
        var anterior = documento();
        when(artistas.obtenerPorId(900L)).thenReturn(new ArtistaResponse(900L, "Soda Stereo", "Rock"));
        mvc.perform(get(ruta())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idArtista").value(900))
                .andExpect(jsonPath("$[0].nombre").value("Soda Stereo"))
                .andExpect(jsonPath("$[0].genero").value("Rock"));
        assertEquals(anterior, documento());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 404, 500, 503})
    void errorDelCatalogoNoModificaFavoritosNiPreferenciasYPermiteRecuperar(int status) throws Exception {
        service.agregarFavorito(id, 900L);
        var anterior = documento();
        var fallo = mock(feign.FeignException.class);
        when(fallo.status()).thenReturn(status);
        when(artistas.obtenerPorId(900L)).thenThrow(fallo);
        mvc.perform(get(ruta())).andExpect(status().is(status == 404 ? 502 : 503));
        assertEquals(anterior, documento());
        mvc.perform(get("/usuarios/" + id + "/preferencias")).andExpect(status().isOk())
                .andExpect(jsonPath("$.artistasFavoritos[0]").value(900))
                .andExpect(jsonPath("$.ciudadPreferida").value("Rosario"));
        // Evita invocar el stub que aun arroja el error al simular la recuperacion.
        doReturn(new ArtistaResponse(900L, "Soda Stereo", "Rock")).when(artistas).obtenerPorId(900L);
        mvc.perform(get(ruta())).andExpect(status().isOk());
        assertEquals(anterior, documento());
    }

    @Test
    void sinFavoritosDevuelveListaVaciaSinLlamarAEventos() throws Exception {
        var anterior = documento();
        mvc.perform(get(ruta())).andExpect(status().isOk()).andExpect(content().json("[]"));
        assertEquals(anterior, documento());
        verifyNoInteractions(artistas);
    }
}
