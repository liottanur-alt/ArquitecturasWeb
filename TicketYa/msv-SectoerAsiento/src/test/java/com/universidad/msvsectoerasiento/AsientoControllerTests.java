package com.universidad.msvsectoerasiento;

import com.universidad.msvsectoerasiento.Entities.*;
import com.universidad.msvsectoerasiento.controller.*;
import com.universidad.msvsectoerasiento.enums.TipoSector;
import com.universidad.msvsectoerasiento.repository.*;
import com.universidad.msvsectoerasiento.service.AsientoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AsientoControllerTests {
    private AsientoRepository asientos;
    private SectorRepository sectores;
    private SectorEventoRepository eventos;
    private AsientoEventoRepository asientosEvento;
    private MockMvc mvc;
    private Sector sector;
    private Asiento asiento;
    private static final String VALID = """
            {"idSector":1,"fila":"A","columna":"1"}
            """;

    @BeforeEach
    void preparar() {
        asientos = mock(AsientoRepository.class);
        sectores = mock(SectorRepository.class);
        eventos = mock(SectorEventoRepository.class);
        asientosEvento = mock(AsientoEventoRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new AsientoController(
                new AsientoService(asientos, sectores, eventos, asientosEvento)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        sector = new Sector();
        sector.setId(1L);
        sector.setTipo(TipoSector.NUMERADO);
        sector.setCapacidadTotal(2);
        asiento = new Asiento();
        asiento.setId(7L);
        asiento.setSector(sector);
        asiento.setFila("A");
        asiento.setColumna("1");
        when(sectores.buscarParaActualizar(1L)).thenReturn(Optional.of(sector));
        when(asientos.findById(7L)).thenReturn(Optional.of(asiento));
        when(asientos.buscarParaActualizar(7L)).thenReturn(Optional.of(asiento));
        when(asientos.buscarIdSector(7L)).thenReturn(Optional.of(1L));
        when(asientos.saveAndFlush(any())).thenAnswer(invocation -> {
            Asiento guardado = invocation.getArgument(0);
            guardado.setId(7L);
            return guardado;
        });
    }

    @Test
    void creaConLocation() throws Exception {
        mvc.perform(post("/asientos").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "http://localhost/asientos/7"))
                .andExpect(jsonPath("$.idSector").value(1));
    }

    @Test
    void rechazaCampo() throws Exception {
        sector.setTipo(TipoSector.CAMPO);
        mvc.perform(post("/asientos").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict());
        verify(asientos, never()).saveAndFlush(any());
    }

    @Test
    void rechazaExcesoDeCapacidad() throws Exception {
        when(asientos.countBySectorId(1L)).thenReturn(2L);
        mvc.perform(post("/asientos").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict());
        verify(asientos, never()).saveAndFlush(any());
    }

    @Test
    void rechazaPosicionDuplicada() throws Exception {
        when(asientos.existsBySectorIdAndFilaAndColumna(1L, "A", "1")).thenReturn(true);
        mvc.perform(post("/asientos").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict());
    }

    @Test
    void validaBody() throws Exception {
        mvc.perform(post("/asientos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(asientos, never()).saveAndFlush(any());
    }

    @Test
    void sectorInexistenteDevuelve404() throws Exception {
        when(sectores.buscarParaActualizar(1L)).thenReturn(Optional.empty());
        mvc.perform(post("/asientos").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isNotFound());
    }

    @Test
    void consultaYFiltra() throws Exception {
        when(asientos.findBySectorId(1L)).thenReturn(List.of(asiento));
        mvc.perform(get("/asientos").param("idSector", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].fila").value("A"));
        mvc.perform(get("/asientos/7")).andExpect(status().isOk());
        mvc.perform(get("/asientos/99")).andExpect(status().isNotFound());
    }

    @Test
    void actualizaPosicion() throws Exception {
        mvc.perform(put("/asientos/7").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.columna").value("1"));
    }

    @Test
    void rechazaActualizacionDuplicada() throws Exception {
        when(asientos.existsBySectorIdAndFilaAndColumnaAndIdNot(1L, "A", "1", 7L)).thenReturn(true);
        mvc.perform(put("/asientos/7").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict());
    }

    @Test
    void protegeAsientoConEvento() throws Exception {
        when(asientosEvento.existsByAsientoId(7L)).thenReturn(true);
        mvc.perform(delete("/asientos/7")).andExpect(status().isConflict());
        verify(asientos, never()).delete(any());
    }

    @Test
    void protegeDistribucionConfigurada() throws Exception {
        when(eventos.existsBySectorId(1L)).thenReturn(true);
        mvc.perform(post("/asientos").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict());
    }

    @Test
    void eliminaSinReferencias() throws Exception {
        mvc.perform(delete("/asientos/7")).andExpect(status().isNoContent());
        verify(asientos).delete(asiento);
    }
}
