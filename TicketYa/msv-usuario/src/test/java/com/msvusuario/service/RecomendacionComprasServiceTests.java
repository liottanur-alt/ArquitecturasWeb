package com.msvusuario.service;

import com.msvusuario.client.CompraClientFeign;
import com.msvusuario.client.EventoClientFeign;
import com.msvusuario.dto.CompraEnComunResponse;
import com.msvusuario.dto.EventoCatalogoResponse;
import com.msvusuario.repository.UsuarioRepository;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecomendacionComprasServiceTests {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final CompraClientFeign compras = mock(CompraClientFeign.class);
    private final EventoClientFeign eventos = mock(EventoClientFeign.class);
    private final Clock reloj = Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
    private final com.msvusuario.client.InventarioClientFeign inventario = mock(com.msvusuario.client.InventarioClientFeign.class);
    private final RecomendacionService service = new RecomendacionService(usuarios,
            mock(RecomendacionPreferenciasService.class), new RecomendacionComprasService(usuarios, compras), reloj, eventos, compras, inventario);

    @BeforeEach
    void usuarioExiste() { com.msvusuario.InventarioPrueba.disponibles(inventario); when(usuarios.existsById(1L)).thenReturn(true); }

    private EventoCatalogoResponse evento(long id, int dias, String estado, String publicacion) {
        return new EventoCatalogoResponse(id, "Evento " + id, "RECITAL", LocalDateTime.now(reloj).plusDays(dias),
                new EventoCatalogoResponse.Lugar("Rosario"), List.of(), estado, publicacion);
    }

    @Test
    void priorizaCantidadLuegoFechaEIdYElMotivoNoExponeCompradores() {
        when(compras.recomendar(1L)).thenReturn(List.of(new CompraEnComunResponse(2L, 3L, 100L),
                new CompraEnComunResponse(3L, 2L, 100L), new CompraEnComunResponse(4L, 2L, 100L),
                new CompraEnComunResponse(5L, 2L, 100L)));
        when(eventos.consultarPorIds(any())).thenReturn(List.of(evento(100, -30, "FINALIZADO", "PUBLICADO"),
                evento(2, 10, "EN_CURSO", "PUBLICADO"), evento(3, 3, "EN_CURSO", "PUBLICADO"),
                evento(5, 2, "EN_CURSO", "PUBLICADO"), evento(4, 2, "EN_CURSO", "PUBLICADO")));
        var resultado = service.recomendarPorCompras(1L).recomendaciones();
        assertEquals(List.of(2L, 4L, 5L, 3L), resultado.stream().map(r -> r.idEvento()).toList());
        assertEquals(3L, resultado.getFirst().compradoresEnComun());
        assertEquals("Clientes que compraron Evento 100 también compraron Evento 2", resultado.getFirst().motivo());
        var json = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(resultado)).get(0);
        assertEquals(7, json.size());
        assertFalse(json.has("idCliente")); assertFalse(json.has("idCompra"));
        verify(usuarios).existsById(1L); verifyNoMoreInteractions(usuarios);
    }

    @Test
    void candidatosDuplicadosNoSumanDosVecesLosMismosVecinos() {
        when(compras.recomendar(1L)).thenReturn(List.of(new CompraEnComunResponse(2L, 3L, 200L),
                new CompraEnComunResponse(2L, 3L, 100L), new CompraEnComunResponse(2L, 2L, 100L)));
        when(eventos.consultarPorIds(any())).thenReturn(List.of(evento(2, 2, "EN_CURSO", "PUBLICADO")));
        var resultado = service.recomendarPorCompras(1L).recomendaciones();
        assertEquals(1, resultado.size()); assertEquals(3L, resultado.getFirst().compradoresEnComun());
        assertEquals("Clientes que compraron el evento #100 también compraron Evento 2", resultado.getFirst().motivo());
    }

    @Test
    void filtraPasadosCanceladosAgotadosSuspendidosYAusentesDelCatalogo() {
        when(compras.recomendar(1L)).thenReturn(java.util.stream.LongStream.rangeClosed(2, 9)
                .mapToObj(id -> new CompraEnComunResponse(id, 1L, 100L)).toList());
        when(eventos.consultarPorIds(any())).thenReturn(List.of(evento(2, -1, "EN_CURSO", "PUBLICADO"),
                evento(3, 2, "CANCELADO", "PUBLICADO"), evento(4, 2, "AGOTADO", "PUBLICADO"),
                evento(5, 2, "EN_CURSO", "SUSPENDIDO"), evento(6, 2, "EN_CURSO", "BORRADOR"),
                evento(7, 2, "FINALIZADO", "PUBLICADO"), evento(8, 0, "EN_CURSO", "PUBLICADO")));
        assertTrue(service.recomendarPorCompras(1L).recomendaciones().isEmpty());
    }

    @Test
    void sinHistorialOCoincidenciasEsVacioSinDependerDeEventos() {
        when(compras.recomendar(1L)).thenReturn(List.of());
        assertTrue(service.recomendarPorCompras(1L).recomendaciones().isEmpty()); verifyNoInteractions(eventos);
    }

    @Test
    void usuarioInexistenteEs404SinConsultarOtrosServicios() {
        var error = assertThrows(ResponseStatusException.class, () -> service.recomendarPorCompras(2L).recomendaciones());
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode()); verifyNoInteractions(compras, eventos);
    }

    @Test
    void comprasCaidoDevuelve503YNoConsultaEventos() {
        var fallo = mock(feign.FeignException.class);
        doThrow(fallo).when(compras).recomendar(1L);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ResponseStatusException.class, () -> service.recomendarPorCompras(1L).recomendaciones()).getStatusCode());
        verifyNoInteractions(eventos);
    }

    @Test
    void eventosCaidoORespuestaNulaEs503() {
        when(compras.recomendar(1L)).thenReturn(List.of(new CompraEnComunResponse(2L, 1L, 100L)));
        var fallo = mock(feign.FeignException.class);
        doThrow(fallo).when(eventos).consultarPorIds(any());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ResponseStatusException.class, () -> service.recomendarPorCompras(1L).recomendaciones()).getStatusCode());
        doReturn(null).when(eventos).consultarPorIds(any());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ResponseStatusException.class, () -> service.recomendarPorCompras(1L).recomendaciones()).getStatusCode());
    }

    static Stream<List<CompraEnComunResponse>> respuestasInvalidas() {
        return Stream.of(null, Collections.singletonList(null), List.of(new CompraEnComunResponse(0L, 1L, 100L)),
                List.of(new CompraEnComunResponse(2L, 0L, 100L)), List.of(new CompraEnComunResponse(2L, 1L, null)),
                List.of(new CompraEnComunResponse(2L, 1L, 2L)));
    }

    @ParameterizedTest
    @MethodSource("respuestasInvalidas")
    void rechazaContratoAgregadoInvalido(List<CompraEnComunResponse> respuesta) {
        when(compras.recomendar(1L)).thenReturn(respuesta);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ResponseStatusException.class, () -> service.recomendarPorCompras(1L).recomendaciones()).getStatusCode());
        verifyNoInteractions(eventos);
    }
}
