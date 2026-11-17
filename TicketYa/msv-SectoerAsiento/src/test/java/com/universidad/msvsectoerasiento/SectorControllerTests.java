package com.universidad.msvsectoerasiento;

import com.universidad.msvsectoerasiento.Entities.Sector;
import com.universidad.msvsectoerasiento.controller.*;
import com.universidad.msvsectoerasiento.enums.TipoSector;
import com.universidad.msvsectoerasiento.repository.*;
import com.universidad.msvsectoerasiento.service.SectorService;
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

class SectorControllerTests {
    private SectorRepository sectores;
    private AsientoRepository asientos;
    private SectorEventoRepository eventos;
    private MockMvc mvc;
    private Sector sector;
    private static final String VALID = """
            {"idLugar":1,"nombre":"Campo","tipo":"CAMPO","capacidadTotal":100}
            """;

    @BeforeEach
    void preparar() {
        sectores = mock(SectorRepository.class);
        asientos = mock(AsientoRepository.class);
        eventos = mock(SectorEventoRepository.class);
        var service = new SectorService(sectores, asientos, eventos);
        mvc = MockMvcBuilders.standaloneSetup(new SectorController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        sector = new Sector();
        sector.setId(1L);
        sector.setIdLugar(1L);
        sector.setNombre("Campo");
        sector.setTipo(TipoSector.CAMPO);
        sector.setCapacidadTotal(100);
    }

    @Test
    void crearDevuelve201YLocation() throws Exception {
        when(sectores.save(any(Sector.class))).thenAnswer(invocation -> {
            Sector nuevo = invocation.getArgument(0);
            nuevo.setId(7L);
            return nuevo;
        });
        mvc.perform(post("/sectores").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "http://localhost/sectores/7"))
                .andExpect(jsonPath("$.id").value(7)).andExpect(jsonPath("$.capacidadTotal").value(100));
    }

    @Test
    void rechazaDatosInvalidosSinPersistir() throws Exception {
        mvc.perform(post("/sectores").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idLugar":0,"nombre":" ","capacidadTotal":-1}
                        """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(sectores);
    }

    @Test
    void inexistenteDevuelve404() throws Exception {
        when(sectores.findById(99L)).thenReturn(Optional.empty());
        mvc.perform(get("/sectores/99")).andExpect(status().isNotFound());
    }

    @Test
    void filtraPorLugar() throws Exception {
        when(sectores.findByIdLugar(1L)).thenReturn(List.of(sector));
        mvc.perform(get("/sectores").param("idLugar", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].nombre").value("Campo"));
        verify(sectores).findByIdLugar(1L);
    }

    @Test
    void impideEliminarConAsientos() throws Exception {
        when(sectores.buscarParaActualizar(1L)).thenReturn(Optional.of(sector));
        when(asientos.existsBySectorId(1L)).thenReturn(true);
        mvc.perform(delete("/sectores/1")).andExpect(status().isConflict());
        verify(sectores, never()).delete(any());
    }

    @Test
    void impideModificarSectorAsociadoAEvento() throws Exception {
        when(sectores.buscarParaActualizar(1L)).thenReturn(Optional.of(sector));
        when(eventos.existsBySectorId(1L)).thenReturn(true);
        mvc.perform(put("/sectores/1").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isConflict());
        verify(sectores, never()).save(any());
    }

    @Test
    void eliminaSectorSinReferencias() throws Exception {
        when(sectores.buscarParaActualizar(1L)).thenReturn(Optional.of(sector));
        mvc.perform(delete("/sectores/1")).andExpect(status().isNoContent());
        verify(sectores).delete(sector);
    }

    @Test
    void actualizaSectorSinReferencias() throws Exception {
        when(sectores.buscarParaActualizar(1L)).thenReturn(Optional.of(sector));
        when(sectores.save(sector)).thenReturn(sector);
        mvc.perform(put("/sectores/1").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
    }
}
