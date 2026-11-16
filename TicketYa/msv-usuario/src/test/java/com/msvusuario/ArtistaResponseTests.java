package com.msvusuario;

import com.msvusuario.dto.ArtistaResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class ArtistaResponseTests {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void aceptaElIdDelServicioRealSinCambiarElContratoDeUsuarios() {
        var artista = mapper.readValue("{\"id\":7,\"nombre\":\"Ana\",\"genero\":\"Rock\"}", ArtistaResponse.class);
        assertEquals(7L, artista.idArtista());
        var respuesta = mapper.readTree(mapper.writeValueAsString(artista));
        assertTrue(respuesta.has("idArtista"));
        assertFalse(respuesta.has("id"));
    }

    @Test
    void sigueAceptandoElContratoDelSimulador() {
        var artista = mapper.readValue("{\"idArtista\":7,\"nombre\":\"Ana\",\"genero\":\"Rock\"}", ArtistaResponse.class);
        assertEquals(7L, artista.idArtista());
    }
}
