package com.msvusuario;

import com.msvusuario.dto.*;
import com.msvusuario.entity.TipoRol;
import com.msvusuario.repository.*;
import com.msvusuario.service.*;
import java.time.*;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class RecomendacionUnificadaIntegrationTests extends TestSecuritySupport {
    @Autowired MockMvc mvc;
    @Autowired UsuarioService usuarios;
    @Autowired UsuarioRepository repository;
    @Autowired RolRepository roles;
    @Autowired MongoTemplate mongo;
    @MockitoBean RecomendacionPreferenciasService preferencias;
    @MockitoBean RecomendacionComprasService compras;
    @MockitoBean Clock reloj;
    @MockitoBean com.msvusuario.client.InventarioClientFeign inventario;
    @MockitoBean com.msvusuario.client.CompraClientFeign clienteCompras;
    private Long id;
    private final LocalDateTime fecha = LocalDateTime.of(2030, 1, 2, 20, 0);

    @BeforeEach
    void preparar() {
        repository.deleteAll();
        InventarioPrueba.disponibles(inventario);
        when(clienteCompras.historialValido(anyLong())).thenReturn(new HistorialValidoResponse(List.of()));
        when(reloj.getZone()).thenReturn(ZoneId.of("America/Argentina/Buenos_Aires"));
        when(reloj.instant()).thenReturn(Instant.parse("2030-01-01T12:00:00Z"));
        id = usuarios.crear(new UsuarioRequest("Ana", "hu6@example.com", "HU6-001", "Tandil",
                roles.findByName(TipoRol.CLIENTE).orElseThrow().getId())).id();
        when(preferencias.recomendar(eq(id), any(), any())).thenReturn(List.of());
        when(compras.recomendar(eq(id), any(), any())).thenReturn(List.of());
    }

    private String ruta() { return "/usuarios/" + id + "/recomendaciones"; }

    private RecomendacionResponse pref(long evento) {
        return new RecomendacionResponse(evento, "Recital", "RECITAL", fecha, "Rosario", 10,
                List.of("Participa un artista favorito", "Coincide con un tipo de evento preferido", "Se realiza en tu ciudad de preferencia"));
    }

    @Test
    void combinaEnUnEventoConMotivosFuentesYSubpuntuacionesSinModificarMongo() throws Exception {
        when(preferencias.recomendar(eq(id), any(), any())).thenReturn(List.of(pref(7)));
        when(compras.recomendar(eq(id), any(), any())).thenReturn(List.of(new RecomendacionCompraResponse(7L, "Recital", "RECITAL", fecha,
                "Rosario", 2, "Clientes que compraron X también compraron Recital")));
        Document anterior = mongo.getCollection("usuarios").find(new Document("_id", id)).first();
        mvc.perform(get(ruta())).andExpect(status().isOk())
                .andExpect(jsonPath("$.parcial").value(false)).andExpect(jsonPath("$.fuentesNoDisponibles").isEmpty())
                .andExpect(jsonPath("$.recomendaciones.length()").value(1))
                .andExpect(jsonPath("$.recomendaciones[0].puntuacion").value(14))
                .andExpect(jsonPath("$.recomendaciones[0].puntuacionPreferencias").value(10))
                .andExpect(jsonPath("$.recomendaciones[0].compradoresEnComun").value(2))
                .andExpect(jsonPath("$.recomendaciones[0].motivos.length()").value(4))
                .andExpect(jsonPath("$.recomendaciones[0].fuentes.length()").value(2));
        assertEquals(anterior, mongo.getCollection("usuarios").find(new Document("_id", id)).first());
    }

    @Test
    void ambasFuentesVaciasSonRespuestaCompleta() throws Exception {
        mvc.perform(get(ruta())).andExpect(status().isOk()).andExpect(jsonPath("$.recomendaciones").isEmpty())
                .andExpect(jsonPath("$.parcial").value(false)).andExpect(jsonPath("$.fuentesNoDisponibles").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void caidaDeUnaFuenteEsRespuestaParcialConResultadosDisponibles(boolean caenCompras) throws Exception {
        if (caenCompras) {
            when(preferencias.recomendar(eq(id), any(), any())).thenReturn(List.of(pref(7)));
            doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(compras).recomendar(eq(id), any(), any());
        } else {
            when(compras.recomendar(eq(id), any(), any())).thenReturn(List.of(new RecomendacionCompraResponse(7L, "Recital", "RECITAL", fecha,
                    "Rosario", 2, "Clientes que compraron X también compraron Recital")));
            doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(preferencias).recomendar(eq(id), any(), any());
        }
        mvc.perform(get(ruta())).andExpect(status().isOk()).andExpect(jsonPath("$.parcial").value(true))
                .andExpect(jsonPath("$.fuentesNoDisponibles[0]").value(caenCompras ? "COMPRAS_EN_COMUN" : "PREFERENCIAS"))
                .andExpect(jsonPath("$.recomendaciones.length()").value(1));
    }

    @Test
    void ambasFuentesCaidasSon503() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(preferencias).recomendar(eq(id), any(), any());
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(compras).recomendar(eq(id), any(), any());
        mvc.perform(get(ruta())).andExpect(status().isServiceUnavailable());
    }

    @Test
    void aplicaDefaultLimitesValidacionY404() throws Exception {
        when(preferencias.recomendar(eq(id), any(), any())).thenReturn(java.util.stream.LongStream.rangeClosed(1, 12).mapToObj(this::pref).toList());
        mvc.perform(get(ruta())).andExpect(status().isOk()).andExpect(jsonPath("$.recomendaciones.length()").value(10));
        mvc.perform(get(ruta()).param("limite", "1")).andExpect(status().isOk()).andExpect(jsonPath("$.recomendaciones.length()").value(1));
        mvc.perform(get(ruta()).param("limite", "100")).andExpect(status().isOk()).andExpect(jsonPath("$.recomendaciones.length()").value(12));
        for (String limite : List.of("0", "-1", "101", "abc")) {
            mvc.perform(get(ruta()).param("limite", limite)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/usuarios/9223372036854775807/recomendaciones")).andExpect(status().isNotFound());
    }
}
