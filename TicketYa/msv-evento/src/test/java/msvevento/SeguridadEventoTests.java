package msvevento;

import msvevento.dto.TurnoFilaResponse;
import msvevento.dto.ValidarTurnoResponse;
import msvevento.entity.EstadoEvento;
import msvevento.entity.EstadoPublicacion;
import msvevento.entity.Evento;
import msvevento.entity.Lugar;
import msvevento.entity.TipoEvento;
import msvevento.repository.EventoRepository;
import msvevento.repository.LugarRepository;
import msvevento.service.FilaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SeguridadEventoTests {

    private static final String AUTH = "Authorization";

    @Autowired MockMvc mvc;
    @Autowired EventoRepository eventos;
    @Autowired LugarRepository lugares;
    @MockitoBean FilaService filas;

    @Test
    void rutaProtegidaRechazaTokenAusenteEInvalido() throws Exception {
        mvc.perform(get("/api/eventos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/eventos").header(AUTH, "Bearer invalido"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void saludPublicaNoRequiereToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void crearEventoRequiereOrganizadorOAdministradorYUsaElSubjectDelJwt() throws Exception {
        Long idLugar = lugar();
        String body = eventoBody(idLugar);

        mvc.perform(post("/api/eventos").header(AUTH, bearer(JwtTestSupport.token("77", "CLIENTE")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/eventos")
                        .header(AUTH, bearer(JwtTestSupport.token("77", "ORGANIZADOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idOrganizador").value(77));
        assertThat(eventos.findAll().stream()
                .filter(evento -> "Concierto".equals(evento.getNombre()))
                .map(Evento::getIdOrganizador))
                .contains(77L);

        mvc.perform(post("/api/eventos").header(AUTH, bearer(JwtTestSupport.token("1", "ADMINISTRADOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idOrganizador").value(1));
    }

    @Test
    void misEventosSoloParaOrganizadorOAdministrador() throws Exception {
        mvc.perform(get("/api/eventos/mis-eventos")
                        .header(AUTH, bearer(JwtTestSupport.token("77", "CLIENTE"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/eventos/mis-eventos")
                        .header(AUTH, bearer(JwtTestSupport.token("77", "ORGANIZADOR"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/eventos/mis-eventos")
                        .header(AUTH, bearer(JwtTestSupport.token("1", "ADMINISTRADOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void rutasInternasSoloAceptanTokenDeServicio() throws Exception {
        Long idEvento = evento().getId();
        String recomendaciones = "{\"idsEventos\":[]}";

        mvc.perform(post("/interno/eventos/recomendaciones/por-ids")
                        .contentType(MediaType.APPLICATION_JSON).content(recomendaciones))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/interno/eventos/recomendaciones/por-ids")
                        .header(AUTH, bearer(JwtTestSupport.token("1", "ADMINISTRADOR")))
                        .contentType(MediaType.APPLICATION_JSON).content(recomendaciones))
                .andExpect(status().isForbidden());
        mvc.perform(post("/interno/eventos/recomendaciones/por-ids")
                        .header(AUTH, bearer(JwtTestSupport.serviceToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(recomendaciones))
                .andExpect(status().isOk());

        mvc.perform(get("/api/eventos/interno/" + idEvento)
                        .header(AUTH, bearer(JwtTestSupport.token("1", "ADMINISTRADOR"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/eventos/interno/" + idEvento)
                        .header(AUTH, bearer(JwtTestSupport.serviceToken())))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/eventos/interno/" + idEvento + "/disponibilidad")
                        .param("agotado", "true")
                        .header(AUTH, bearer(JwtTestSupport.serviceToken())))
                .andExpect(status().isNoContent());
    }

    @Test
    void filaPublicaUsaElSubjectDelJwt() throws Exception {
        when(filas.entrar(eq(9L), eq(77L))).thenReturn(new TurnoFilaResponse(9L, "t1", "ADMITIDO", 0L, "abc", null));
        when(filas.consultar(eq(9L), eq(77L))).thenReturn(new TurnoFilaResponse(9L, "t1", "ADMITIDO", 0L, "abc", null));

        mvc.perform(post("/api/eventos/9/fila")
                        .header(AUTH, bearer(JwtTestSupport.token("77", "CLIENTE")))
                        .header("X-Organizador-Id", "999"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/eventos/9/fila/mi-turno")
                        .header(AUTH, bearer(JwtTestSupport.token("77", "CLIENTE")))
                        .header("X-Organizador-Id", "999"))
                .andExpect(status().isOk());

        verify(filas).entrar(9L, 77L);
        verify(filas).consultar(9L, 77L);
    }

    @Test
    void filaInternaUsaElClaimUsuarioIdEIgnoraElCuerpo() throws Exception {
        String body = """
                {"eventoId":9,"clienteId":999,"autorizacionCompra":"abc","idOperacion":"op-1"}
                """;
        when(filas.validarAutorizacion(eq(9L), eq(55L), eq("abc"), eq("op-1")))
                .thenReturn(new ValidarTurnoResponse(true, false, null));
        when(filas.completarCompra(eq(9L), eq(55L), eq("abc"), eq("op-1")))
                .thenReturn(new ValidarTurnoResponse(true, false, null));

        mvc.perform(post("/interno/fila/autorizaciones/validar")
                        .header(AUTH, bearer(JwtTestSupport.serviceToken(55L)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/interno/fila/autorizaciones/completar")
                        .header(AUTH, bearer(JwtTestSupport.serviceToken(55L)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        verify(filas).validarAutorizacion(9L, 55L, "abc", "op-1");
        verify(filas).completarCompra(9L, 55L, "abc", "op-1");

        mvc.perform(post("/interno/fila/autorizaciones/validar")
                        .header(AUTH, bearer(JwtTestSupport.serviceToken()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/interno/fila/autorizaciones/validar")
                        .header(AUTH, bearer(JwtTestSupport.token("55", "CLIENTE")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/interno/fila/autorizaciones/validar")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    private Long lugar() {
        Lugar lugar = new Lugar();
        lugar.setNombre("Teatro");
        lugar.setCiudad("Tandil");
        return lugares.saveAndFlush(lugar).getId();
    }

    private Evento evento() {
        Evento evento = new Evento();
        evento.setNombre("Evento interno");
        evento.setFechaHora(LocalDateTime.now().plusDays(1));
        evento.setEstado(EstadoEvento.EN_CURSO);
        evento.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);
        evento.setTipo(TipoEvento.RECITAL);
        evento.setLugar(lugares.findById(lugar()).orElseThrow());
        return eventos.saveAndFlush(evento);
    }

    private String eventoBody(Long idLugar) {
        return """
                {"nombre":"Concierto","descripcion":"Prueba","tipo":"RECITAL",
                 "idOrganizador":999,"participantesIds":[],
                 "fechaHora":"2030-01-01T20:00:00","idLugar":%d,"demanda":false}
                """.formatted(idLugar);
    }

    private String bearer(String token) {
        return JwtTestSupport.bearer(token);
    }
}
