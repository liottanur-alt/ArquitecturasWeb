package org.msvcompraentradasol.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.msvcompraentradasol.dtos.CancelacionEventoResponseDTO;
import org.msvcompraentradasol.dtos.CompraResponseDTO;
import org.msvcompraentradasol.dtos.EntradaResponseDTO;
import org.msvcompraentradasol.dtos.SolicitudResponseDTO;
import org.msvcompraentradasol.enums.EstadoSolicitud;
import org.msvcompraentradasol.services.CompraService;
import org.msvcompraentradasol.services.EntradaService;
import org.msvcompraentradasol.services.SolicitudService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompraService compraService;

    @MockitoBean
    private EntradaService entradaService;

    @MockitoBean
    private SolicitudService solicitudService;

    @Test
    void crearCompraDevuelve201() throws Exception {
        when(compraService.crearCompra(any())).thenReturn(compra(5L, "PENDIENTE"));

        mockMvc.perform(post("/compras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "claveIdempotencia": "clave-http-1",
                                  "idCliente": 2,
                                  "idEvento": 1,
                                  "idSector": 3,
                                  "idsAsientos": [11, 12],
                                  "medioDePago": "TARJETA"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.estadoCompra").value("PENDIENTE"))
                .andExpect(jsonPath("$.idsAsientos[0]").value(11));
    }

    @Test
    void crearCompraSinClaveDevuelve400() throws Exception {
        mockMvc.perform(post("/compras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idCliente": 2,
                                  "idEvento": 1,
                                  "idSector": 3,
                                  "idsAsientos": [11],
                                  "medioDePago": "TARJETA"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Solicitud inválida"));
    }

    @Test
    void crearCompraSinAsientosNiCantidadDevuelve400() throws Exception {
        mockMvc.perform(post("/compras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "claveIdempotencia": "clave-http-2",
                                  "idCliente": 2,
                                  "idEvento": 1,
                                  "idSector": 3,
                                  "medioDePago": "TARJETA"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle").value(
                        org.hamcrest.Matchers.containsString("Debe indicar")
                ));
    }

    @Test
    void buscarCompraDevuelve200() throws Exception {
        when(compraService.buscarPorId(5L)).thenReturn(compra(5L, "APROBADA"));

        mockMvc.perform(get("/compras/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.estadoCompra").value("APROBADA"));
    }

    @Test
    void buscarCompraInexistenteDevuelve404() throws Exception {
        when(compraService.buscarPorId(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra no encontrada"));

        mockMvc.perform(get("/compras/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Compra no encontrada"));
    }

    @Test
    void conflictoDeNegocioDevuelve409() throws Exception {
        when(compraService.buscarPorId(7L))
                .thenThrow(new IllegalStateException("La compra ya fue procesada con otro resultado"));

        mockMvc.perform(get("/compras/7"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "La compra ya fue procesada con otro resultado"
                ));
    }

    @Test
    void procesarPagoDevuelve200() throws Exception {
        when(compraService.procesarPago(eq(5L), eq(true))).thenReturn(compra(5L, "APROBADA"));

        mockMvc.perform(put("/compras/5/pago").param("aprobado", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoCompra").value("APROBADA"));
    }

    @Test
    void cancelarEventoDevuelveResumen() throws Exception {
        when(compraService.cancelarEvento(1L)).thenReturn(
                new CancelacionEventoResponseDTO(1L, 2, 4, new BigDecimal("6000.00"), true)
        );

        mockMvc.perform(post("/compras/eventos/1/cancelacion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comprasCanceladas").value(2))
                .andExpect(jsonPath("$.entradasReintegradas").value(4))
                .andExpect(jsonPath("$.inventarioCancelado").value(true));
    }

    @Test
    void listarComprasPorClienteDevuelve200() throws Exception {
        when(compraService.listarPorCliente(2L)).thenReturn(List.of(compra(5L, "APROBADA")));

        mockMvc.perform(get("/compras/cliente/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5));
    }

    @Test
    void buscarEntradaPorIdYPorQr() throws Exception {
        when(entradaService.buscarPorId(1L)).thenReturn(entrada("NO_ESCANEADO"));
        when(entradaService.buscarPorQr("qr-1")).thenReturn(entrada("NO_ESCANEADO"));

        mockMvc.perform(get("/entradas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qr").value("qr-1"));
        mockMvc.perform(get("/entradas/qr/qr-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void marcarEntradaUsadaDevuelve200() throws Exception {
        when(entradaService.marcarUsada(1L)).thenReturn(entrada("ESCANEADO"));

        mockMvc.perform(patch("/entradas/1/uso"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoQr").value("ESCANEADO"));
    }

    @Test
    void crearSolicitudDevuelve201() throws Exception {
        when(solicitudService.crearSolicitud(any())).thenReturn(solicitud("PENDIENTE"));

        mockMvc.perform(post("/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idEntrada": 10,
                                  "idCliente": 2,
                                  "descripcion": "No puedo asistir"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estadoSolicitud").value("PENDIENTE"));
    }

    @Test
    void crearSolicitudSinDescripcionDevuelve400() throws Exception {
        mockMvc.perform(post("/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idEntrada": 10,
                                  "idCliente": 2
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Solicitud inválida"));
    }

    @Test
    void resolverSolicitudDevuelve200() throws Exception {
        when(solicitudService.resolverSolicitud(3L, 9L, EstadoSolicitud.ACEPTADA))
                .thenReturn(solicitud("ACEPTADA"));

        mockMvc.perform(put("/solicitudes/3/resolver")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idAdministrador": 9,
                                  "estadoSolicitud": "ACEPTADA"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoSolicitud").value("ACEPTADA"));
    }

    @Test
    void listarSolicitudesPendientesDevuelve200() throws Exception {
        when(solicitudService.listarPendientes()).thenReturn(List.of(solicitud("PENDIENTE")));

        mockMvc.perform(get("/solicitudes/pendientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estadoSolicitud").value("PENDIENTE"));
    }

    @Test
    void respetaYDevuelveElCorrelationId() throws Exception {
        when(compraService.buscarPorId(5L)).thenReturn(compra(5L, "PENDIENTE"));

        mockMvc.perform(get("/compras/5").header("X-Correlation-Id", "prueba-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "prueba-123"));
    }

    private static CompraResponseDTO compra(Long id, String estado) {
        return new CompraResponseDTO(
                id,
                2L,
                1L,
                3L,
                2L,
                "TARJETA",
                "Compra de prueba",
                estado,
                "clave-http-1",
                55L,
                new BigDecimal("1500.00"),
                new BigDecimal("3000.00"),
                "SIM-PAGO",
                null,
                null,
                List.of(11L, 12L)
        );
    }

    private static EntradaResponseDTO entrada(String estadoQr) {
        return new EntradaResponseDTO(
                1L,
                "qr-1",
                estadoQr,
                2L,
                1L,
                3L,
                11L,
                new BigDecimal("1500.00"),
                "PAGA"
        );
    }

    private static SolicitudResponseDTO solicitud(String estado) {
        return new SolicitudResponseDTO(
                3L,
                10L,
                20L,
                2L,
                "ACEPTADA".equals(estado) ? 9L : null,
                Instant.parse("2026-01-01T10:00:00Z"),
                "ACEPTADA".equals(estado) ? Instant.parse("2026-01-02T10:00:00Z") : null,
                "ACEPTADA".equals(estado) ? "SIM-REF-1" : null,
                "ACEPTADA".equals(estado) ? "COMPLETADO" : null,
                "No puedo asistir",
                estado
        );
    }
}
