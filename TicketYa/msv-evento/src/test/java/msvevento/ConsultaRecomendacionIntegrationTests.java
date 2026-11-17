package msvevento;

import jakarta.persistence.EntityManager;
import msvevento.dto.*;
import msvevento.entity.*;
import msvevento.repository.*;
import msvevento.service.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ConsultaRecomendacionIntegrationTests {
    @Autowired EventoRepository eventos;
    @Autowired LugarRepository lugares;
    @Autowired ArtistaRepository artistas;
    @Autowired ConsultaRecomendacionService consultas;
    @Autowired InicializarCiudadesNormalizadas inicializador;
    @Autowired EntityManager entityManager;
    @Autowired MockMvc mvc;
    private final LocalDateTime ahora = LocalDateTime.of(2030, 1, 1, 12, 0);

    Lugar lugar(String ciudad) {
        Lugar lugar = new Lugar(); lugar.setNombre(ciudad); lugar.setCiudad(ciudad);
        return lugares.saveAndFlush(lugar);
    }

    Artista artista() {
        Artista artista = new Artista(); artista.setNombre("Artista"); artista.setGenero("Rock");
        return artistas.saveAndFlush(artista);
    }

    Evento evento(TipoEvento tipo, Lugar lugar, Artista... participantes) {
        Evento evento = new Evento(); evento.setNombre("Evento"); evento.setDescripcion("Prueba");
        evento.setTipo(tipo); evento.setLugar(lugar); evento.setParticipantes(List.of(participantes));
        evento.setFechaHora(ahora.plusDays(1)); evento.setEstado(EstadoEvento.EN_CURSO);
        evento.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);
        return eventos.saveAndFlush(evento);
    }

    @Test
    void coincidePorArtistaTipoOCiudadConNormalizacionSinExigirLosTres() {
        Artista favorito = artista(), otro = artista(), segundoFavorito = artista();
        Lugar cordoba = lugar(" CÓRDOBA "), mendoza = lugar("Mendoza");
        Evento porArtista = evento(TipoEvento.OTRO, mendoza, favorito, otro);
        Evento porTipo = evento(TipoEvento.RECITAL, mendoza, otro);
        Evento porCiudad = evento(TipoEvento.OTRO, cordoba, otro);
        Evento porTodo = evento(TipoEvento.RECITAL, cordoba, favorito, segundoFavorito);
        evento(TipoEvento.OTRO, mendoza, otro);
        List<EventoRecomendacionResponse> resultado = consultas.candidatos(new CandidatosRecomendacionRequest(
                List.of(TipoEvento.RECITAL), List.of(favorito.getId(), segundoFavorito.getId()), "cordoba", ahora));
        assertEquals(List.of(porArtista.getId(), porTipo.getId(), porCiudad.getId(), porTodo.getId()),
                resultado.stream().map(EventoRecomendacionResponse::id).toList());
        assertEquals(2, resultado.getFirst().participantes().size());
        assertEquals(2, resultado.getLast().participantes().size());
    }

    @Test
    void descartaPasadosAgotadosCanceladosYNoPublicados() {
        Lugar lugar = lugar("Tandil");
        Evento pasado = evento(TipoEvento.RECITAL, lugar); pasado.setFechaHora(ahora);
        Evento agotado = evento(TipoEvento.RECITAL, lugar); agotado.setEstado(EstadoEvento.AGOTADO);
        Evento cancelado = evento(TipoEvento.RECITAL, lugar); cancelado.setEstado(EstadoEvento.CANCELADO);
        Evento borrador = evento(TipoEvento.RECITAL, lugar); borrador.setEstadoPublicacion(EstadoPublicacion.BORRADOR);
        Evento suspendido = evento(TipoEvento.RECITAL, lugar); suspendido.setEstadoPublicacion(EstadoPublicacion.SUSPENDIDO);
        eventos.flush();
        assertTrue(consultas.candidatos(new CandidatosRecomendacionRequest(List.of(TipoEvento.RECITAL), List.of(), null, ahora)).isEmpty());
    }

    @Test
    void consultaPorIdsDeduplicaYConservaReferenciasPasadasPublicadas() {
        Lugar lugar = lugar("Rosario");
        Evento pasado = evento(TipoEvento.RECITAL, lugar); pasado.setFechaHora(ahora.minusDays(1));
        Evento borrador = evento(TipoEvento.RECITAL, lugar); borrador.setEstadoPublicacion(EstadoPublicacion.BORRADOR);
        eventos.flush();
        List<EventoRecomendacionResponse> resultado = consultas.porIds(new EventosPorIdsRequest(
                List.of(pasado.getId(), pasado.getId(), borrador.getId(), Long.MAX_VALUE)));
        assertEquals(List.of(pasado.getId()), resultado.stream().map(EventoRecomendacionResponse::id).toList());
        assertTrue(consultas.porIds(new EventosPorIdsRequest(List.of())).isEmpty());
        assertTrue(consultas.candidatos(new CandidatosRecomendacionRequest(List.of(), List.of(), " ", ahora)).isEmpty());
    }

    @Test
    void sinLimiteAnticipadoYNingunaConsultaPorCadaEvento() {
        Lugar lugar = lugar("Tandil"); Artista artista = artista();
        for (int i = 0; i < 201; i++) evento(TipoEvento.RECITAL, lugar, artista);
        entityManager.clear();
        org.hibernate.stat.Statistics estadisticas = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();
        assertEquals(201, consultas.candidatos(new CandidatosRecomendacionRequest(List.of(TipoEvento.RECITAL), List.of(), null, ahora)).size());
        assertEquals(1, estadisticas.getPrepareStatementCount());
    }

    @Test
    void inicializadorCompletaCiudadesExistentesSinModificarElTextoVisible() {
        Lugar lugar = lugar("  Buenos   Áires  ");
        evento(TipoEvento.OTRO, lugar);
        entityManager.createNativeQuery("update lugares set ciudad_normalizada=null where id=:id").setParameter("id", lugar.getId()).executeUpdate();
        entityManager.clear(); inicializador.run(null); entityManager.flush();
        assertEquals(1, consultas.candidatos(new CandidatosRecomendacionRequest(List.of(), List.of(), "buenos aires", ahora)).size());
        assertEquals("  Buenos   Áires  ", lugares.findById(lugar.getId()).orElseThrow().getCiudad());
    }

    @Test
    void endpointsValidanEntradaYDevuelvenContratoMinimo() throws Exception {
        Evento evento = evento(TipoEvento.RECITAL, lugar("Tandil"));
        mvc.perform(post("/interno/eventos/recomendaciones/por-ids").contentType("application/json")
                .content("{\"idsEventos\":[" + evento.getId() + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(evento.getId()))
                .andExpect(jsonPath("$[0].descripcion").doesNotExist()).andExpect(jsonPath("$[0].idOrganizador").doesNotExist());
        for (String invalido : List.of("{}", "{\"idsEventos\":[0]}", "{\"idsEventos\":[null]}")) {
            mvc.perform(post("/interno/eventos/recomendaciones/por-ids").contentType("application/json").content(invalido))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/interno/eventos/recomendaciones/candidatos").contentType("application/json")
                .content("{\"tiposEventoPreferidos\":[\"INVALIDO\"],\"artistasFavoritos\":[],\"fechaDesde\":\"2030-01-01T12:00:00\"}"))
                .andExpect(status().isBadRequest());
    }
}
