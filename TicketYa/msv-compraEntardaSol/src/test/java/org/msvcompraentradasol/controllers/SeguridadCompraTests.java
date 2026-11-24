package org.msvcompraentradasol.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.msvcompraentradasol.JwtTestSupport;
import org.msvcompraentradasol.dtos.EntradaResponseDTO;
import org.msvcompraentradasol.dtos.SolicitudResponseDTO;
import org.msvcompraentradasol.enums.EstadoSolicitud;
import org.msvcompraentradasol.services.EntradaService;
import org.msvcompraentradasol.services.RecomendacionCompraService;
import org.msvcompraentradasol.services.SolicitudService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeguridadCompraTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EntradaService entradaService;

    @MockitoBean
    private SolicitudService solicitudService;

    @MockitoBean
    private RecomendacionCompraService recomendaciones;

    @Test
    void rutaProtegidaRechazaTokenAusenteEInvalido() throws Exception {
        perform(get("/compras/5"), null).andExpect(status().isUnauthorized());
        perform(get("/compras/5"), "token-invalido").andExpect(status().isUnauthorized());
    }

    @Test
    void saludPublicaNoRequiereToken() throws Exception {
        perform(get("/actuator/health"), null).andExpect(status().isOk());
    }

    @Test
    void lecturaDeQrSoloParaPersonalYAdministrador() throws Exception {
        when(entradaService.buscarPorQr("qr-1")).thenReturn(entrada());
        String cliente = JwtTestSupport.bearer(JwtTestSupport.token("77", "CLIENTE"));

        perform(get("/entradas/qr/qr-1"), cliente).andExpect(status().isForbidden());
        perform(get("/entradas/qr/qr-1"), JwtTestSupport.bearer(
                JwtTestSupport.token("77", "PERSONAL_ACCESO"))).andExpect(status().isOk());
        perform(get("/entradas/qr/qr-1"), JwtTestSupport.bearer(
                JwtTestSupport.token("77", "ADMINISTRADOR"))).andExpect(status().isOk());
    }

    @Test
    void resolverSolicitudSoloParaAdministrador() throws Exception {
        when(solicitudService.resolverSolicitud(eq(3L), eq(77L), eq(EstadoSolicitud.ACEPTADA)))
                .thenReturn(solicitud());
        String cuerpo = """
                {"idAdministrador":999,"estadoSolicitud":"ACEPTADA"}
                """;

        perform(put("/solicitudes/3/resolver").contentType(MediaType.APPLICATION_JSON).content(cuerpo),
                JwtTestSupport.bearer(JwtTestSupport.token("77", "CLIENTE")))
                .andExpect(status().isForbidden());
        perform(put("/solicitudes/3/resolver").contentType(MediaType.APPLICATION_JSON).content(cuerpo),
                JwtTestSupport.bearer(JwtTestSupport.token("77", "ADMINISTRADOR")))
                .andExpect(status().isOk());
    }

    @Test
    void rutasInternasSoloAceptanTokenDeServicio() throws Exception {
        when(recomendaciones.recomendar(77L)).thenReturn(List.of());
        String cliente = JwtTestSupport.bearer(JwtTestSupport.token("77", "CLIENTE"));
        String servicio = JwtTestSupport.bearer(JwtTestSupport.serviceToken(77L));

        perform(get("/interno/recomendaciones/clientes/77"), null)
                .andExpect(status().isForbidden());
        perform(get("/interno/recomendaciones/clientes/77"), cliente)
                .andExpect(status().isForbidden());
        perform(get("/interno/recomendaciones/clientes/77"), servicio)
                .andExpect(status().isOk());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String authorization) throws Exception {
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request);
    }

    private static EntradaResponseDTO entrada() {
        return new EntradaResponseDTO(1L, "qr-1", "NO_ESCANEADO", 77L, 1L, 3L, 11L,
                new BigDecimal("1500.00"), "PAGA");
    }

    private static SolicitudResponseDTO solicitud() {
        return new SolicitudResponseDTO(3L, 10L, 20L, 77L, 77L, null, null, null, null,
                "No puedo asistir", "ACEPTADA");
    }
}
