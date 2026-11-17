package com.msvusuario.service;

import com.msvusuario.client.EventoClientFeign;
import com.msvusuario.dto.EventoCatalogoResponse;
import com.msvusuario.entity.TipoEvento;
import com.msvusuario.entity.Usuario;
import com.msvusuario.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecomendacionPreferenciasServiceTests {
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"),
            ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final LocalDateTime AHORA = LocalDateTime.now(RELOJ);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final EventoClientFeign eventos = mock(EventoClientFeign.class);
    private final com.msvusuario.client.InventarioClientFeign inventario = mock(com.msvusuario.client.InventarioClientFeign.class);
    private final RecomendacionService service = new RecomendacionService(usuarios,
            new RecomendacionPreferenciasService(usuarios), mock(RecomendacionComprasService.class), RELOJ, eventos, mock(com.msvusuario.client.CompraClientFeign.class), inventario);
    private Usuario usuario;

    @BeforeEach
    void preferencias() {
        com.msvusuario.InventarioPrueba.disponibles(inventario);
        usuario = new Usuario();
        usuario.setCiudad("Rosario");
        usuario.setCiudadPreferida("Córdoba");
        usuario.getTiposEventoPreferidos().add(TipoEvento.RECITAL);
        usuario.getArtistasFavoritos().addAll(Set.of(9L, 10L));
        when(usuarios.findById(1L)).thenReturn(Optional.of(usuario));
    }

    private static EventoCatalogoResponse evento(long id, String tipo, String ciudad, Long... artistas) {
        return new EventoCatalogoResponse(id, "Evento " + id, tipo, AHORA.plusDays(1),
                new EventoCatalogoResponse.Lugar(ciudad),
                Arrays.stream(artistas).map(EventoCatalogoResponse.Participante::new).toList(),
                "EN_CURSO", "PUBLICADO");
    }

    private static EventoCatalogoResponse conEstado(EventoCatalogoResponse e, String estado, String publicacion) {
        return new EventoCatalogoResponse(e.id(), e.nombre(), e.tipo(), e.fechaHora(),
                e.lugar(), e.participantes(), estado, publicacion);
    }

    private static EventoCatalogoResponse conFecha(EventoCatalogoResponse e, LocalDateTime fecha) {
        return new EventoCatalogoResponse(e.id(), e.nombre(), e.tipo(), fecha,
                e.lugar(), e.participantes(), e.estado(), e.estadoPublicacion());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void sumaCadaCriterioUnaVezYExplicaTodasLasCoincidencias(int criterios) {
        String tipo = (criterios & 1) != 0 ? "RECITAL" : "TEATRO";
        String ciudad = (criterios & 2) != 0 ? "cordoba" : "Mendoza";
        Long[] artistas = (criterios & 4) != 0 ? new Long[]{9L, 10L, 9L} : new Long[]{11L};
        when(eventos.buscarCandidatos(any())).thenReturn(List.of(evento(1, tipo, ciudad, artistas)));
        var resultado = service.recomendarPorPreferencias(1L).recomendaciones();
        int puntuacion = ((criterios & 1) != 0 ? 3 : 0) + ((criterios & 2) != 0 ? 2 : 0)
                + ((criterios & 4) != 0 ? 5 : 0);
        if (puntuacion == 0) {
            assertTrue(resultado.isEmpty());
        } else {
            assertEquals(1, resultado.size());
            assertEquals(puntuacion, resultado.getFirst().puntuacion());
            assertEquals(Integer.bitCount(criterios), resultado.getFirst().motivos().size());
            if ((criterios & 4) != 0) assertTrue(resultado.getFirst().motivos().contains("Participa un artista favorito"));
        }
        verify(usuarios).findById(1L);
        verifyNoMoreInteractions(usuarios);
        assertEquals(Set.of(9L, 10L), usuario.getArtistasFavoritos());
    }

    @Test
    void ordenaPorPuntosFechaEIdSinDependerDelOrdenDelCatalogo() {
        var lejos = conFecha(evento(5, "RECITAL", "Cordoba", 9L), AHORA.plusDays(2));
        var catalogo = new ArrayList<>(List.of(lejos, evento(3, "RECITAL", "Cordoba", 9L),
                evento(2, "RECITAL", "Cordoba", 9L), evento(1, "TEATRO", "Mendoza", 9L),
                evento(6, "RECITAL", "Mendoza", 11L), evento(7, "TEATRO", "Cordoba", 11L)));
        var random = new Random(42);
        for (int i = 0; i < 10; i++) {
            Collections.shuffle(catalogo, random);
            when(eventos.buscarCandidatos(any())).thenReturn(List.copyOf(catalogo));
            assertEquals(List.of(2L, 3L, 5L, 1L, 6L, 7L),
                    service.recomendarPorPreferencias(1L).recomendaciones().stream().map(r -> r.idEvento()).toList());
        }
    }

    @Test
    void eliminaDuplicadosConservandoLaMejorCoincidencia() {
        var mejor = evento(1, "RECITAL", "Cordoba", 9L);
        when(eventos.buscarCandidatos(any())).thenReturn(List.of(evento(1, "TEATRO", "Cordoba", 11L),
                mejor, mejor, conEstado(mejor, "CANCELADO", "PUBLICADO")));
        var resultado = service.recomendarPorPreferencias(1L).recomendaciones();
        assertEquals(1, resultado.size());
        assertEquals(10, resultado.getFirst().puntuacion());
    }

    @Test
    void copiasDelMismoIdConPuntosIgualesTienenSeleccionDeterminista() {
        var porArtista = evento(1, "TEATRO", "Mendoza", 9L);
        var porTipoYCiudad = evento(1, "RECITAL", "Cordoba", 11L);
        when(eventos.buscarCandidatos(any())).thenReturn(List.of(porArtista, porTipoYCiudad));
        var primera = service.recomendarPorPreferencias(1L).recomendaciones();
        when(eventos.buscarCandidatos(any())).thenReturn(List.of(porTipoYCiudad, porArtista));
        assertEquals(primera, service.recomendarPorPreferencias(1L).recomendaciones());
        assertEquals(1, primera.size());
    }

    static Stream<EventoCatalogoResponse> noVendibles() {
        var e = evento(1, "RECITAL", "Cordoba", 9L);
        return Stream.concat(Stream.of("CANCELADO", "FINALIZADO", "AGOTADO", "DESCONOCIDO", null)
                        .map(estado -> conEstado(e, estado, "PUBLICADO")),
                Stream.of(conEstado(e, "EN_CURSO", "BORRADOR"), conEstado(e, "EN_CURSO", "SUSPENDIDO"),
                        conEstado(e, "EN_CURSO", null), conFecha(e, AHORA), conFecha(e, AHORA.minusSeconds(1)),
                        conFecha(e, null), null));
    }

    @ParameterizedTest
    @MethodSource("noVendibles")
    void excluyePasadosCanceladosAgotadosYNoPublicados(EventoCatalogoResponse evento) {
        when(eventos.buscarCandidatos(any())).thenReturn(Collections.singletonList(evento));
        assertTrue(service.recomendarPorPreferencias(1L).recomendaciones().isEmpty());
    }

    @Test
    void usaLaZonaDeEventosYNoLaZonaUtcDelContenedor() {
        var fechaLocal = AHORA.plusMinutes(1); // 09:01 de Argentina, aunque en UTC ya son las 12:00.
        when(eventos.buscarCandidatos(any())).thenReturn(List.of(conFecha(evento(1, "RECITAL", "Cordoba"), fechaLocal)));
        assertEquals(1, service.recomendarPorPreferencias(1L).recomendaciones().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CÓRDOBA", " cordoba ", "Córdoba"})
    void normalizaMayusculasTildesYEspaciosEnCiudad(String ciudad) {
        when(eventos.buscarCandidatos(any())).thenReturn(List.of(evento(1, "TEATRO", ciudad)));
        assertEquals(2, service.recomendarPorPreferencias(1L).recomendaciones().getFirst().puntuacion());
    }

    @Test
    void normalizaEspaciosInternosDeCiudad() {
        usuario.setCiudadPreferida(" Buenos   Aires ");
        when(eventos.buscarCandidatos(any())).thenReturn(List.of(evento(1, "TEATRO", "buenos aires")));
        assertEquals(2, service.recomendarPorPreferencias(1L).recomendaciones().getFirst().puntuacion());
    }

    @Test
    void preferenciasVaciasNoUsanLaCiudadDelPerfilNiDanRecomendacionesGenericas() {
        usuario.setTiposEventoPreferidos(null);
        usuario.setArtistasFavoritos(null);
        usuario.setCiudadPreferida(null);
        when(eventos.buscarCandidatos(any())).thenReturn(List.of(evento(1, "RECITAL", "Rosario", 9L)));
        assertTrue(service.recomendarPorPreferencias(1L).recomendaciones().isEmpty());
    }

    @Test
    void catalogoSinEventosDevuelveVacioYParticipantesIncompletosNoDanError() {
        when(eventos.buscarCandidatos(any())).thenReturn(List.of());
        assertTrue(service.recomendarPorPreferencias(1L).recomendaciones().isEmpty());
        var e = evento(1, "RECITAL", "Mendoza", (Long) null);
        when(eventos.buscarCandidatos(any())).thenReturn(List.of(e));
        assertEquals(3, service.recomendarPorPreferencias(1L).recomendaciones().getFirst().puntuacion());
    }

    @Test
    void usuarioInexistenteEs404SinLlamarAEventos() {
        when(usuarios.findById(2L)).thenReturn(Optional.empty());
        var error = assertThrows(ResponseStatusException.class, () -> service.recomendarPorPreferencias(2L).recomendaciones());
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(eventos);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 404, 500, 503})
    void falloAlConsultarCatalogoEs503YNoDevuelveUnaListaVaciaEnganosa(int status) {
        var fallo = mock(feign.FeignException.class);
        when(fallo.status()).thenReturn(status);
        doThrow(fallo).when(eventos).buscarCandidatos(any());
        var error = assertThrows(ResponseStatusException.class, () -> service.recomendarPorPreferencias(1L).recomendaciones());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        verify(usuarios, never()).save(any());
        assertEquals(Set.of(9L, 10L), usuario.getArtistasFavoritos());
    }

    @Test
    void respuestaNulaDelCatalogoEs503() {
        when(eventos.buscarCandidatos(any())).thenReturn(null);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ResponseStatusException.class, () -> service.recomendarPorPreferencias(1L).recomendaciones()).getStatusCode());
    }
}
