package com.msvusuario.service;

import com.msvusuario.dto.*;
import com.msvusuario.repository.UsuarioRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecomendacionServiceTests {
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final RecomendacionPreferenciasService preferencias = mock(RecomendacionPreferenciasService.class);
    private final RecomendacionComprasService compras = mock(RecomendacionComprasService.class);
    private final Clock reloj = Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
    private final com.msvusuario.client.CompraClientFeign clienteCompras = mock(com.msvusuario.client.CompraClientFeign.class);
    private final com.msvusuario.client.InventarioClientFeign inventario = mock(com.msvusuario.client.InventarioClientFeign.class);
    private final RecomendacionService service = new RecomendacionService(usuarios, preferencias, compras, reloj, mock(com.msvusuario.client.EventoClientFeign.class), clienteCompras, inventario);
    private final LocalDateTime manana = LocalDateTime.now(reloj).plusDays(1);

    @BeforeEach
    void preparar() {
        com.msvusuario.InventarioPrueba.disponibles(inventario);
        when(usuarios.existsById(1L)).thenReturn(true);
        when(clienteCompras.historialValido(1L)).thenReturn(new com.msvusuario.dto.HistorialValidoResponse(List.of()));
        when(preferencias.recomendar(eq(1L), any(), any())).thenReturn(List.of());
        when(compras.recomendar(eq(1L), any(), any())).thenReturn(List.of());
    }

    private RecomendacionResponse pref(long id, int puntos) {
        return new RecomendacionResponse(id, "Evento " + id, "RECITAL", manana, "Rosario", puntos,
                List.of("Participa un artista favorito", "Coincide con un tipo de evento preferido"));
    }

    private RecomendacionCompraResponse compra(long id, long cantidad) {
        return new RecomendacionCompraResponse(id, "Evento " + id, "RECITAL", manana, "Rosario", cantidad,
                "Clientes que compraron X también compraron Evento " + id);
    }

    @Test
    void combinaAntesDeLimitarYPreservaMotivosYFuentesSinDuplicados() {
        when(preferencias.recomendar(eq(1L), any(), any())).thenReturn(List.of(pref(1, 10), pref(2, 8), pref(3, 7), pref(3, 7)));
        when(compras.recomendar(eq(1L), any(), any())).thenReturn(List.of(compra(4, 5), compra(3, 3), compra(1, 1), compra(3, 3)));
        RecomendacionesResponse respuesta = service.recomendar(1L, 10);
        assertFalse(respuesta.parcial()); assertTrue(respuesta.fuentesNoDisponibles().isEmpty());
        assertEquals(List.of(3L, 1L, 4L, 2L), respuesta.recomendaciones().stream().map(r -> r.idEvento()).toList());
        RecomendacionUnificadaResponse compartida = respuesta.recomendaciones().getFirst();
        assertEquals(13, compartida.puntuacion()); assertEquals(7, compartida.puntuacionPreferencias());
        assertEquals(3, compartida.compradoresEnComun()); assertEquals(3, compartida.motivos().size());
        assertEquals(List.of(FuenteRecomendacion.PREFERENCIAS, FuenteRecomendacion.COMPRAS_EN_COMUN), compartida.fuentes());
        assertTrue(compartida.motivos().contains("Clientes que compraron X también compraron Evento 3"));
        assertEquals(3L, service.recomendar(1L, 1).recomendaciones().getFirst().idEvento());
        verify(usuarios, never()).save(any());
    }

    @Test
    void puntuacionDeComprasTieneTopeSinOverflowYNoSumaFilasRepetidas() {
        when(preferencias.recomendar(eq(1L), any(), any())).thenReturn(List.of(pref(1, 10)));
        when(compras.recomendar(eq(1L), any(), any())).thenReturn(List.of(compra(1, Long.MAX_VALUE), compra(1, 2)));
        RecomendacionUnificadaResponse r = service.recomendar(1L, 10).recomendaciones().getFirst();
        assertEquals(20, r.puntuacion()); assertEquals(Long.MAX_VALUE, r.compradoresEnComun());
        assertEquals(3, r.motivos().size());
    }

    @Test
    void empataPorFechaEIdIndependientementeDeLaFuente() {
        RecomendacionResponse lejano = new RecomendacionResponse(5L, "Evento 5", "RECITAL", manana.plusDays(1), "Rosario", 10, List.of("Tipo"));
        when(preferencias.recomendar(eq(1L), any(), any())).thenReturn(List.of(lejano, pref(3, 10), pref(2, 10)));
        when(compras.recomendar(eq(1L), any(), any())).thenReturn(List.of(compra(4, 5)));
        assertEquals(List.of(2L, 3L, 4L, 5L), service.recomendar(1L, 10).recomendaciones().stream().map(r -> r.idEvento()).toList());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void usaLaOtraFuenteSiUnaNoTieneDatos(boolean conPreferencias) {
        if (conPreferencias) when(preferencias.recomendar(eq(1L), any(), any())).thenReturn(List.of(pref(1, 8)));
        else when(compras.recomendar(eq(1L), any(), any())).thenReturn(List.of(compra(1, 2)));
        RecomendacionesResponse r = service.recomendar(1L, 10);
        assertFalse(r.parcial()); assertEquals(1, r.recomendaciones().size());
        assertEquals(List.of(conPreferencias ? FuenteRecomendacion.PREFERENCIAS : FuenteRecomendacion.COMPRAS_EN_COMUN),
                r.recomendaciones().getFirst().fuentes());
    }

    @Test
    void ambasVaciasSonExitoCompletoConListaVacia() {
        RecomendacionesResponse r = service.recomendar(1L, 10);
        assertTrue(r.recomendaciones().isEmpty()); assertFalse(r.parcial());
    }

    @ParameterizedTest
    @EnumSource(FuenteRecomendacion.class)
    void unaFuenteCaidaDa200ParcialConLosResultadosDeLaOtra(FuenteRecomendacion caida) {
        if (caida == FuenteRecomendacion.PREFERENCIAS) {
            doThrow(noDisponible()).when(preferencias).recomendar(eq(1L), any(), any());
            when(compras.recomendar(eq(1L), any(), any())).thenReturn(List.of(compra(1, 2)));
        } else {
            doThrow(noDisponible()).when(compras).recomendar(eq(1L), any(), any());
            when(preferencias.recomendar(eq(1L), any(), any())).thenReturn(List.of(pref(1, 8)));
        }
        RecomendacionesResponse r = service.recomendar(1L, 10);
        assertTrue(r.parcial()); assertEquals(List.of(caida), r.fuentesNoDisponibles());
        assertEquals(1, r.recomendaciones().size());
    }

    @ParameterizedTest
    @EnumSource(FuenteRecomendacion.class)
    void fuenteVaciaExitosaYOtraCaidaNoSeConfundenConAmbasCaidas(FuenteRecomendacion caida) {
        if (caida == FuenteRecomendacion.PREFERENCIAS) doThrow(noDisponible()).when(preferencias).recomendar(eq(1L), any(), any());
        else doThrow(noDisponible()).when(compras).recomendar(eq(1L), any(), any());
        RecomendacionesResponse r = service.recomendar(1L, 10);
        assertTrue(r.recomendaciones().isEmpty()); assertTrue(r.parcial());
        assertEquals(List.of(caida), r.fuentesNoDisponibles());
    }

    @Test
    void ambasCaidasSon503() {
        doThrow(noDisponible()).when(preferencias).recomendar(eq(1L), any(), any());
        doThrow(noDisponible()).when(compras).recomendar(eq(1L), any(), any());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ResponseStatusException.class, () -> service.recomendar(1L, 10)).getStatusCode());
    }

    @Test
    void resultadoNuloDeUnaFuenteSeInformaComoParcial() {
        when(preferencias.recomendar(eq(1L), any(), any())).thenReturn(null);
        when(compras.recomendar(eq(1L), any(), any())).thenReturn(List.of(compra(1, 2)));
        assertEquals(List.of(FuenteRecomendacion.PREFERENCIAS), service.recomendar(1L, 10).fuentesNoDisponibles());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101, Integer.MAX_VALUE})
    void limitesInvalidosSon400SinConsultarFuentes(int limite) {
        assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ResponseStatusException.class, () -> service.recomendar(1L, limite)).getStatusCode());
        verifyNoInteractions(preferencias, compras);
    }

    @Test
    void usuarioInexistenteEs404SinConsultarFuentes() {
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.recomendar(2L, 10)).getStatusCode());
        verifyNoInteractions(preferencias, compras);
    }

    @Test
    void noOculta404NiErroresDeProgramacionComoIndisponibilidad() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND)).when(preferencias).recomendar(eq(1L), any(), any());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, () -> service.recomendar(1L, 10)).getStatusCode());
        doThrow(new IllegalStateException("Error inesperado")).when(preferencias).recomendar(eq(1L), any(), any());
        assertThrows(IllegalStateException.class, () -> service.recomendar(1L, 10));
    }

    @Test
    void descartaResultadosQueYaPasaronMientrasConsultabaLasFuentes() {
        when(preferencias.recomendar(eq(1L), any(), any())).thenReturn(List.of(new RecomendacionResponse(1L, "Pasado", "RECITAL",
                LocalDateTime.now(reloj).minusSeconds(1), "Rosario", 10, List.of("Tipo"))));
        when(compras.recomendar(eq(1L), any(), any())).thenReturn(List.of(compra(2, 1)));
        assertEquals(List.of(2L), service.recomendar(1L, 100).recomendaciones().stream().map(r -> r.idEvento()).toList());
    }

    private ResponseStatusException noDisponible() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE); }

    @Test
    void excluyeCompradosDeAmbasFuentesAntesDeOrdenarYLimitar() {
        when(preferencias.recomendar(eq(1L), any(), any())).thenReturn(List.of(pref(1, 10), pref(2, 8), pref(3, 7)));
        when(compras.recomendar(eq(1L), any(), any())).thenReturn(List.of(compra(1, 5), compra(4, 5)));
        when(clienteCompras.historialValido(1L)).thenReturn(new HistorialValidoResponse(List.of(1L, 1L, 4L)));
        RecomendacionesResponse respuesta = service.recomendar(1L, 1);
        assertEquals(List.of(2L), respuesta.recomendaciones().stream().map(RecomendacionUnificadaResponse::idEvento).toList());
        assertTrue(respuesta.historialVerificado()); assertFalse(respuesta.parcial());
        verify(clienteCompras, times(1)).historialValido(1L);
    }

    @Test
    void trasReintegrarTodoElEventoPuedeVolverASerRecomendado() {
        when(preferencias.recomendar(eq(1L), any(), any())).thenReturn(List.of(pref(1, 10)));
        when(clienteCompras.historialValido(1L)).thenReturn(new HistorialValidoResponse(List.of(1L)),
                new HistorialValidoResponse(List.of()));
        assertTrue(service.recomendar(1L, 10).recomendaciones().isEmpty());
        assertEquals(1L, service.recomendar(1L, 10).recomendaciones().getFirst().idEvento());
    }

    @Test
    void historialInaccesibleNoSeConfundeConHistorialVacioVerificado() {
        when(preferencias.recomendar(eq(1L), any(), any())).thenReturn(List.of(pref(1, 10)));
        when(clienteCompras.historialValido(1L)).thenThrow(mock(feign.FeignException.class));
        RecomendacionesResponse respuesta = service.recomendar(1L, 10);
        assertTrue(respuesta.parcial()); assertFalse(respuesta.historialVerificado());
        assertTrue(respuesta.fuentesNoDisponibles().isEmpty());
        assertEquals(1, respuesta.recomendaciones().size());
    }

    @Test
    void comprasCaidoPermitePreferenciasConHistorialNoVerificado() {
        when(preferencias.recomendar(eq(1L), any(), any())).thenReturn(List.of(pref(1, 10)));
        doThrow(noDisponible()).when(compras).recomendar(eq(1L), any(), any());
        when(clienteCompras.historialValido(1L)).thenThrow(mock(feign.FeignException.class));
        RecomendacionesResponse respuesta = service.recomendar(1L, 10);
        assertTrue(respuesta.parcial()); assertFalse(respuesta.historialVerificado());
        assertEquals(List.of(FuenteRecomendacion.COMPRAS_EN_COMUN), respuesta.fuentesNoDisponibles());
        assertEquals(10, respuesta.recomendaciones().getFirst().puntuacion());
    }

    @Test
    void historialInvalidoNoSeDaPorVerificado() {
        List<HistorialValidoResponse> invalidos = java.util.Arrays.asList(null,
                new HistorialValidoResponse(null), new HistorialValidoResponse(List.of(0L)),
                new HistorialValidoResponse(java.util.Arrays.asList(1L, null)), new HistorialValidoResponse(List.of(-1L)));
        for (HistorialValidoResponse invalido : invalidos) {
            when(clienteCompras.historialValido(1L)).thenReturn(invalido);
            RecomendacionesResponse respuesta = service.recomendar(1L, 10);
            assertFalse(respuesta.historialVerificado()); assertTrue(respuesta.parcial());
        }
    }

    @Test
    void consultaInventarioUnaVezSoloParaCandidatosNoCompradosYAntesDelLimite() {
        when(preferencias.recomendar(eq(1L), any(), any())).thenReturn(List.of(pref(1, 10), pref(2, 8), pref(3, 7)));
        when(compras.recomendar(eq(1L), any(), any())).thenReturn(List.of(compra(2, 5), compra(4, 5)));
        when(clienteCompras.historialValido(1L)).thenReturn(new HistorialValidoResponse(List.of(1L)));
        when(inventario.consultar(any())).thenReturn(List.of(new DisponibilidadEventoResponse(2L, false),
                new DisponibilidadEventoResponse(3L, true), new DisponibilidadEventoResponse(4L, true)));
        RecomendacionesResponse respuesta = service.recomendar(1L, 1);
        assertEquals(4L, respuesta.recomendaciones().getFirst().idEvento());
        assertTrue(respuesta.disponibilidadVerificada()); assertFalse(respuesta.parcial());
        verify(inventario, times(1)).consultar(new DisponibilidadLoteRequest(List.of(2L, 3L, 4L)));
    }

    @Test
    void todosAgotadosEsVacioVerificadoSinRespuestaParcial() {
        when(preferencias.recomendar(eq(1L), any(), any())).thenReturn(List.of(pref(1, 10)));
        when(inventario.consultar(any())).thenReturn(List.of(new DisponibilidadEventoResponse(1L, false)));
        RecomendacionesResponse respuesta = service.recomendar(1L, 10);
        assertTrue(respuesta.recomendaciones().isEmpty()); assertTrue(respuesta.disponibilidadVerificada());
        assertFalse(respuesta.parcial());
    }

    @Test
    void inventarioCaidoConservaCandidatosConDisponibilidadNoVerificada() {
        when(preferencias.recomendar(eq(1L), any(), any())).thenReturn(List.of(pref(1, 10)));
        when(inventario.consultar(any())).thenThrow(mock(feign.FeignException.class));
        RecomendacionesResponse respuesta = service.recomendar(1L, 10);
        assertEquals(1, respuesta.recomendaciones().size()); assertTrue(respuesta.parcial());
        assertFalse(respuesta.disponibilidadVerificada()); assertTrue(respuesta.historialVerificado());
        assertTrue(respuesta.fuentesNoDisponibles().isEmpty());
    }

    @Test
    void sinCandidatosNoConsultaInventario() {
        RecomendacionesResponse respuesta = service.recomendar(1L, 10);
        assertTrue(respuesta.disponibilidadVerificada()); assertFalse(respuesta.parcial());
        verifyNoInteractions(inventario);
    }

    @Test
    void loteIncompletoDuplicadoAjenoONuloNoSeDaPorVerificado() {
        when(preferencias.recomendar(eq(1L), any(), any())).thenReturn(List.of(pref(1, 10)));
        List<List<DisponibilidadEventoResponse>> invalidos = Arrays.asList(null, List.of(),
                Collections.singletonList(null), List.of(new DisponibilidadEventoResponse(null, true)),
                List.of(new DisponibilidadEventoResponse(1L, null)), List.of(new DisponibilidadEventoResponse(9L, true)),
                List.of(new DisponibilidadEventoResponse(1L, true), new DisponibilidadEventoResponse(1L, true)));
        for (List<DisponibilidadEventoResponse> invalido : invalidos) {
            when(inventario.consultar(any())).thenReturn(invalido);
            RecomendacionesResponse respuesta = service.recomendar(1L, 10);
            assertTrue(respuesta.parcial()); assertFalse(respuesta.disponibilidadVerificada());
            assertEquals(1, respuesta.recomendaciones().size());
        }
    }
}
