package com.msvusuario;

import com.msvusuario.client.EventoClientFeign;
import com.msvusuario.dto.EventoCatalogoResponse;
import com.msvusuario.dto.PreferenciasRequest;
import com.msvusuario.dto.UsuarioRequest;
import com.msvusuario.dto.CompraEnComunResponse;
import com.msvusuario.dto.FuenteRecomendacion;
import com.msvusuario.dto.RecomendacionesResponse;
import com.msvusuario.service.RecomendacionService;
import com.msvusuario.entity.TipoEvento;
import com.msvusuario.entity.TipoRol;
import com.msvusuario.repository.RolRepository;
import com.msvusuario.repository.UsuarioRepository;
import com.msvusuario.service.UsuarioService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecomendacionIntegrationTests extends MongoIntegrationSupport {
    @Autowired MockMvc mvc;
    @Autowired UsuarioService usuarios;
    @Autowired UsuarioRepository repository;
    @Autowired RolRepository roles;
    @Autowired MongoTemplate mongo;
    @Autowired RecomendacionService recomendaciones;
    @MockitoBean EventoClientFeign eventos;
    @MockitoBean com.msvusuario.client.CompraClientFeign compras;
    @MockitoBean Clock reloj;
    @MockitoBean com.msvusuario.client.InventarioClientFeign inventario;
    private Long id;

    private List<EventoCatalogoResponse> catalogoCompartido() {
        return List.of(new EventoCatalogoResponse(7L, "Recital", "RECITAL",
                LocalDateTime.of(2030, 1, 2, 20, 0), new EventoCatalogoResponse.Lugar("Rosario"),
                List.of(new EventoCatalogoResponse.Participante(900L)), "EN_CURSO", "PUBLICADO"),
                new EventoCatalogoResponse(100L, "Recital anterior", "RECITAL",
                        LocalDateTime.of(2029, 1, 2, 20, 0), null, List.of(), "FINALIZADO", "PUBLICADO"));
    }

    @Test
    void historialComunExcluyeEventosDeUnificadasYEndpointsIndividuales() throws Exception {
        candidatosCompartidos();
        when(compras.historialValido(id)).thenReturn(new com.msvusuario.dto.HistorialValidoResponse(List.of(7L)));
        when(eventos.buscarCandidatos(any())).thenReturn(catalogoCompartido());
        mvc.perform(get("/usuarios/" + id + "/recomendaciones"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.historialVerificado").value(true))
                .andExpect(jsonPath("$.parcial").value(false)).andExpect(jsonPath("$.recomendaciones").isEmpty());
        for (String fuente : List.of("por-preferencias", "por-compras")) {
            mvc.perform(get("/usuarios/" + id + "/recomendaciones/" + fuente))
                    .andExpect(status().isOk()).andExpect(content().json("[]"))
                    .andExpect(header().string("X-Historial-Verificado", "true"))
                    .andExpect(header().string("X-Respuesta-Parcial", "false"));
        }
        verify(eventos, times(2)).buscarCandidatos(any());
        verify(compras, times(3)).historialValido(id);
    }

    @Test
    void historialCaidoConservaPreferenciasEInformaDegradacionEnJsonYCabeceras() throws Exception {
        when(compras.recomendar(id)).thenThrow(mock(feign.FeignException.class));
        when(compras.historialValido(id)).thenThrow(mock(feign.FeignException.class));
        when(eventos.buscarCandidatos(any())).thenReturn(catalogoCompartido());
        mvc.perform(get("/usuarios/" + id + "/recomendaciones"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.historialVerificado").value(false))
                .andExpect(jsonPath("$.parcial").value(true))
                .andExpect(jsonPath("$.fuentesNoDisponibles[0]").value("COMPRAS_EN_COMUN"))
                .andExpect(jsonPath("$.recomendaciones[0].idEvento").value(7));
        mvc.perform(get("/usuarios/" + id + "/recomendaciones/por-preferencias"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].idEvento").value(7))
                .andExpect(header().string("X-Historial-Verificado", "false"))
                .andExpect(header().string("X-Respuesta-Parcial", "true"));
    }

    @Test
    void inventarioAgotadoExcluyeEventoDeAmbasFuentesYLasRutasIndividuales() throws Exception {
        candidatosCompartidos();
        when(eventos.buscarCandidatos(any())).thenReturn(catalogoCompartido());
        when(inventario.consultar(any())).thenReturn(List.of(new com.msvusuario.dto.DisponibilidadEventoResponse(7L, false)));
        mvc.perform(get("/usuarios/" + id + "/recomendaciones"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recomendaciones").isEmpty())
                .andExpect(jsonPath("$.parcial").value(false)).andExpect(jsonPath("$.disponibilidadVerificada").value(true));
        for (String fuente : List.of("por-preferencias", "por-compras")) {
            mvc.perform(get("/usuarios/" + id + "/recomendaciones/" + fuente))
                    .andExpect(status().isOk()).andExpect(content().json("[]"))
                    .andExpect(header().string("X-Disponibilidad-Verificada", "true"));
        }
        verify(inventario, times(3)).consultar(new com.msvusuario.dto.DisponibilidadLoteRequest(List.of(7L)));
        verify(eventos, times(2)).buscarCandidatos(any());
    }

    @Test
    void inventarioCaidoDaRespuestaParcialEnJsonYCabecerasSinPerderCandidatos() throws Exception {
        candidatosCompartidos();
        when(eventos.buscarCandidatos(any())).thenReturn(catalogoCompartido());
        when(inventario.consultar(any())).thenThrow(mock(feign.FeignException.class));
        mvc.perform(get("/usuarios/" + id + "/recomendaciones"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recomendaciones[0].puntuacion").value(14))
                .andExpect(jsonPath("$.disponibilidadVerificada").value(false)).andExpect(jsonPath("$.parcial").value(true))
                .andExpect(jsonPath("$.historialVerificado").value(true));
        mvc.perform(get("/usuarios/" + id + "/recomendaciones/por-preferencias"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].idEvento").value(7))
                .andExpect(header().string("X-Disponibilidad-Verificada", "false"))
                .andExpect(header().string("X-Respuesta-Parcial", "true"));
    }

    private void candidatosCompartidos() {
        when(compras.recomendar(id)).thenReturn(List.of(new CompraEnComunResponse(7L, 2L, 100L)));
    }

    @Test
    void hu10ReutilizaPreferenciasYConsultaEnUnLoteSoloLosIdsFaltantes() throws Exception {
        when(eventos.buscarCandidatos(any())).thenReturn(List.of(catalogoCompartido().getFirst()));
        EventoCatalogoResponse porCompras = new EventoCatalogoResponse(8L, "Festival", "FESTIVAL",
                LocalDateTime.of(2030, 1, 3, 20, 0), new EventoCatalogoResponse.Lugar("Mendoza"),
                List.of(), "EN_CURSO", "PUBLICADO");
        when(compras.recomendar(id)).thenReturn(List.of(new CompraEnComunResponse(7L, 2L, 100L),
                new CompraEnComunResponse(8L, 3L, 100L)));
        when(eventos.consultarPorIds(any())).thenReturn(List.of(porCompras, catalogoCompartido().getLast()));
        mvc.perform(get("/usuarios/" + id + "/recomendaciones").param("limite", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recomendaciones.length()").value(1))
                .andExpect(jsonPath("$.recomendaciones[0].idEvento").value(7))
                .andExpect(jsonPath("$.recomendaciones[0].puntuacion").value(14))
                .andExpect(jsonPath("$.recomendaciones[0].motivos[3]").value("Clientes que compraron Recital anterior también compraron Recital"));
        verify(eventos, times(1)).buscarCandidatos(new com.msvusuario.dto.CandidatosRecomendacionRequest(
                List.of("RECITAL"), List.of(900L), "Rosario", LocalDateTime.of(2030, 1, 1, 9, 0)));
        verify(eventos, times(1)).consultarPorIds(new com.msvusuario.dto.EventosPorIdsRequest(List.of(8L, 100L)));
        verify(inventario).consultar(new com.msvusuario.dto.DisponibilidadLoteRequest(List.of(7L, 8L)));
    }

    @Test
    void hu10BusquedaCaidaPuedeConservarComprasDisponiblesPorLote() throws Exception {
        candidatosCompartidos();
        when(eventos.buscarCandidatos(any())).thenThrow(mock(feign.FeignException.class));
        when(eventos.consultarPorIds(any())).thenReturn(catalogoCompartido());
        mvc.perform(get("/usuarios/" + id + "/recomendaciones"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parcial").value(true))
                .andExpect(jsonPath("$.fuentesNoDisponibles[0]").value("PREFERENCIAS"))
                .andExpect(jsonPath("$.recomendaciones[0].puntuacion").value(4));
        verify(eventos, times(1)).buscarCandidatos(any()); verify(eventos, times(1)).consultarPorIds(any());
    }

    @Test
    void hu10LoteCaidoNoEliminaResultadosDeLaBusqueda() throws Exception {
        candidatosCompartidos();
        when(eventos.buscarCandidatos(any())).thenReturn(List.of(catalogoCompartido().getFirst()));
        when(eventos.consultarPorIds(any())).thenThrow(mock(feign.FeignException.class));
        mvc.perform(get("/usuarios/" + id + "/recomendaciones"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parcial").value(true))
                .andExpect(jsonPath("$.fuentesNoDisponibles[0]").value("COMPRAS_EN_COMUN"))
                .andExpect(jsonPath("$.recomendaciones[0].puntuacion").value(10));
    }

    @Test
    void hu10SinPreferenciasNiCandidatosNoConsultaEventos() throws Exception {
        usuarios.actualizarPreferencias(id, new PreferenciasRequest(List.of(), null));
        usuarios.eliminarFavorito(id, 900L);
        when(compras.recomendar(id)).thenReturn(List.of());
        mvc.perform(get("/usuarios/" + id + "/recomendaciones"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parcial").value(false))
                .andExpect(jsonPath("$.recomendaciones").isEmpty());
        verifyNoInteractions(eventos, inventario);
    }

    @Test
    void reutilizaCandidatosRecibidosSinVolverAPedirSusIds() throws Exception {
        candidatosCompartidos();
        // Una segunda consulta perderia el evento: las dos fuentes deben usar la primera respuesta.
        when(eventos.buscarCandidatos(any())).thenReturn(catalogoCompartido(), List.of());
        mvc.perform(get("/usuarios/" + id + "/recomendaciones"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parcial").value(false))
                .andExpect(jsonPath("$.recomendaciones.length()").value(1))
                .andExpect(jsonPath("$.recomendaciones[0].puntuacion").value(14))
                .andExpect(jsonPath("$.recomendaciones[0].puntuacionPreferencias").value(10))
                .andExpect(jsonPath("$.recomendaciones[0].compradoresEnComun").value(2))
                .andExpect(jsonPath("$.recomendaciones[0].motivos[3]")
                        .value("Clientes que compraron Recital anterior también compraron Recital"))
                .andExpect(jsonPath("$.recomendaciones[0].fuentes.length()").value(2));
        verify(eventos, times(1)).buscarCandidatos(any());
        verify(compras, times(1)).recomendar(id);
    }

    @Test
    void endpointsIndividualesConservanCalculosYConsultanUnaVezPorSolicitud() throws Exception {
        candidatosCompartidos();
        when(eventos.buscarCandidatos(any())).thenReturn(catalogoCompartido());
        when(eventos.consultarPorIds(any())).thenReturn(catalogoCompartido());
        mvc.perform(get("/usuarios/" + id + "/recomendaciones/por-preferencias"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].puntuacion").value(10))
                .andExpect(jsonPath("$[0].motivos.length()").value(3));
        verify(compras, never()).recomendar(anyLong());
        verify(eventos, times(1)).buscarCandidatos(any());
        mvc.perform(get("/usuarios/" + id + "/recomendaciones/por-compras"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].compradoresEnComun").value(2))
                .andExpect(jsonPath("$[0].motivo").value("Clientes que compraron Recital anterior también compraron Recital"));
        verify(eventos, times(1)).consultarPorIds(any());
        mvc.perform(get("/usuarios/" + id + "/recomendaciones"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recomendaciones[0].puntuacion").value(14));
        verify(eventos, times(2)).buscarCandidatos(any());
    }

    @Test
    void falloDeAmbasConsultasEs503YLaSolicitudSiguienteSeRecupera() throws Exception {
        candidatosCompartidos();
        when(eventos.buscarCandidatos(any())).thenThrow(mock(feign.FeignException.class)).thenReturn(catalogoCompartido());
        when(eventos.consultarPorIds(any())).thenThrow(mock(feign.FeignException.class));
        mvc.perform(get("/usuarios/" + id + "/recomendaciones")).andExpect(status().isServiceUnavailable());
        verify(eventos, times(1)).buscarCandidatos(any());
        mvc.perform(get("/usuarios/" + id + "/recomendaciones"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parcial").value(false))
                .andExpect(jsonPath("$.recomendaciones[0].puntuacion").value(14));
        verify(eventos, times(2)).buscarCandidatos(any());
    }

    @Test
    void ambasConsultasNulasSon503SinReintentos() throws Exception {
        candidatosCompartidos();
        when(eventos.buscarCandidatos(any())).thenReturn(null, catalogoCompartido());
        when(eventos.consultarPorIds(any())).thenReturn(null);
        mvc.perform(get("/usuarios/" + id + "/recomendaciones")).andExpect(status().isServiceUnavailable());
        verify(eventos, times(1)).buscarCandidatos(any());
    }

    @Test
    void sinCandidatosDeComprasElFalloCompartidoPermiteRespuestaParcialVacia() {
        when(compras.recomendar(id)).thenReturn(List.of());
        when(eventos.buscarCandidatos(any())).thenThrow(mock(feign.FeignException.class));
        RecomendacionesResponse respuesta = recomendaciones.recomendar(id, 10);
        assertTrue(respuesta.parcial());
        assertTrue(respuesta.recomendaciones().isEmpty());
        assertEquals(List.of(FuenteRecomendacion.PREFERENCIAS), respuesta.fuentesNoDisponibles());
        verify(eventos, times(1)).buscarCandidatos(any());
    }

    @Test
    void comprasCaidoConservaPreferenciasYUnaUnicaConsultaDeEventos() {
        when(compras.recomendar(id)).thenThrow(mock(feign.FeignException.class));
        when(eventos.buscarCandidatos(any())).thenReturn(catalogoCompartido());
        RecomendacionesResponse respuesta = recomendaciones.recomendar(id, 10);
        assertTrue(respuesta.parcial());
        assertEquals(List.of(FuenteRecomendacion.COMPRAS_EN_COMUN), respuesta.fuentesNoDisponibles());
        assertEquals(10, respuesta.recomendaciones().getFirst().puntuacion());
        verify(eventos, times(1)).buscarCandidatos(any());
    }

    @Test
    void solicitudesSucesivasNoCompartenCacheDelCatalogo() {
        when(compras.recomendar(id)).thenReturn(List.of());
        when(eventos.buscarCandidatos(any())).thenReturn(catalogoCompartido(), List.of());
        assertEquals(1, recomendaciones.recomendar(id, 10).recomendaciones().size());
        assertTrue(recomendaciones.recomendar(id, 10).recomendaciones().isEmpty());
        verify(eventos, times(2)).buscarCandidatos(any());
    }

    @Test
    void usuarioInexistenteOLimiteInvalidoNoConsultaElCatalogo() throws Exception {
        mvc.perform(get("/usuarios/9223372036854775807/recomendaciones")).andExpect(status().isNotFound());
        mvc.perform(get("/usuarios/" + id + "/recomendaciones").param("limite", "0"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(eventos, compras);
    }

    @BeforeEach
    void preparar() {
        repository.deleteAll();
        InventarioPrueba.disponibles(inventario);
        when(eventos.consultarPorIds(any(com.msvusuario.dto.EventosPorIdsRequest.class))).thenAnswer(invocacion -> {
            com.msvusuario.dto.EventosPorIdsRequest request = invocacion.getArgument(0);
            return catalogoCompartido().stream().filter(e -> request.idsEventos().contains(e.id())).toList();
        });
        when(reloj.getZone()).thenReturn(ZoneId.of("America/Argentina/Buenos_Aires"));
        when(reloj.instant()).thenReturn(Instant.parse("2030-01-01T12:00:00Z"));
        var rol = roles.findByName(TipoRol.CLIENTE).orElseThrow().getId();
        when(compras.historialValido(anyLong())).thenReturn(new com.msvusuario.dto.HistorialValidoResponse(List.of()));
        id = usuarios.crear(new UsuarioRequest("Ana", "hu4@example.com", "HU4-001", "Tandil", rol)).id();
        usuarios.actualizarPreferencias(id, new PreferenciasRequest(List.of(TipoEvento.RECITAL), "Rosario"));
        usuarios.agregarFavorito(id, 900L);
    }

    @Test
    void endpointDevuelvePuntuacionMotivosYDatosSinModificarMongo() throws Exception {
        var anterior = mongo.getCollection("usuarios").find(new Document("_id", id)).first();
        when(eventos.buscarCandidatos(any())).thenReturn(List.of(new EventoCatalogoResponse(7L, "Recital", "RECITAL",
                LocalDateTime.of(2030, 1, 2, 20, 0), new EventoCatalogoResponse.Lugar("Rosario"),
                List.of(new EventoCatalogoResponse.Participante(900L)), "EN_CURSO", "PUBLICADO")));
        mvc.perform(get("/usuarios/" + id + "/recomendaciones/por-preferencias")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].idEvento").value(7))
                .andExpect(jsonPath("$[0].puntuacion").value(10))
                .andExpect(jsonPath("$[0].motivos.length()").value(3))
                .andExpect(jsonPath("$[0].nombre").value("Recital"))
                .andExpect(jsonPath("$[0].ciudad").value("Rosario"));
        assertEquals(anterior, mongo.getCollection("usuarios").find(new Document("_id", id)).first());
    }

    @Test
    void sinCoincidenciasEs200Vacio() throws Exception {
        when(eventos.buscarCandidatos(any())).thenReturn(List.of(new EventoCatalogoResponse(7L, "Deporte", "DEPORTE",
                LocalDateTime.of(2030, 1, 2, 20, 0), new EventoCatalogoResponse.Lugar("Cordoba"),
                List.of(), "EN_CURSO", "PUBLICADO")));
        mvc.perform(get("/usuarios/" + id + "/recomendaciones/por-preferencias")).andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void usuarioInexistenteEs404() throws Exception {
        mvc.perform(get("/usuarios/9223372036854775807/recomendaciones/por-preferencias")).andExpect(status().isNotFound());
        verifyNoInteractions(eventos);
    }

    @Test
    void eventosCaidoEs503SinCambiarPreferencias() throws Exception {
        var anterior = mongo.getCollection("usuarios").find(new Document("_id", id)).first();
        var fallo = mock(feign.FeignException.class);
        when(fallo.status()).thenReturn(-1);
        doThrow(fallo).when(eventos).buscarCandidatos(any());
        mvc.perform(get("/usuarios/" + id + "/recomendaciones/por-preferencias")).andExpect(status().isServiceUnavailable());
        assertEquals(anterior, mongo.getCollection("usuarios").find(new Document("_id", id)).first());
    }

    @Test
    void comprasEnComunDevuelvenSoloEventosCantidadYMotivo() throws Exception {
        when(compras.recomendar(id)).thenReturn(List.of(new com.msvusuario.dto.CompraEnComunResponse(8L, 3L, 7L)));
        when(eventos.consultarPorIds(any())).thenReturn(List.of(
                new EventoCatalogoResponse(7L, "Recital X", "RECITAL", LocalDateTime.of(2029, 1, 1, 20, 0),
                        null, List.of(), "FINALIZADO", "PUBLICADO"),
                new EventoCatalogoResponse(8L, "Recital Y", "RECITAL", LocalDateTime.of(2030, 1, 2, 20, 0),
                        new EventoCatalogoResponse.Lugar("Rosario"), List.of(), "EN_CURSO", "PUBLICADO")));
        var anterior = mongo.getCollection("usuarios").find(new Document("_id", id)).first();
        mvc.perform(get("/usuarios/" + id + "/recomendaciones/por-compras")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idEvento").value(8))
                .andExpect(jsonPath("$[0].compradoresEnComun").value(3))
                .andExpect(jsonPath("$[0].motivo").value("Clientes que compraron Recital X también compraron Recital Y"))
                .andExpect(jsonPath("$[0].idCliente").doesNotExist())
                .andExpect(jsonPath("$[0].idCompra").doesNotExist());
        assertEquals(anterior, mongo.getCollection("usuarios").find(new Document("_id", id)).first());
    }

    @Test
    void comprasEnComunSinHistorialYUsuarioInexistente() throws Exception {
        when(compras.recomendar(id)).thenReturn(List.of());
        mvc.perform(get("/usuarios/" + id + "/recomendaciones/por-compras")).andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mvc.perform(get("/usuarios/9223372036854775807/recomendaciones/por-compras")).andExpect(status().isNotFound());
        verifyNoInteractions(eventos);
    }

    @Test
    void comprasEnComunInformanIndisponibilidad() throws Exception {
        doThrow(mock(feign.FeignException.class)).when(compras).recomendar(id);
        mvc.perform(get("/usuarios/" + id + "/recomendaciones/por-compras")).andExpect(status().isServiceUnavailable());
    }
}
