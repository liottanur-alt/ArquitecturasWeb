package org.msvcompraentradasol.feigns;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.msvcompraentradasol.dtos.DevolucionRequestDTO;
import org.msvcompraentradasol.dtos.EventoFechaResponseDTO;
import org.msvcompraentradasol.dtos.ReservaRequestDTO;
import org.msvcompraentradasol.dtos.ReservaResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifica que los DTOs de Feign sigan los contratos publicados por Eventos y SectorAsiento.
 * Si cambia un nombre de campo en el otro microservicio, estos tests fallan.
 */
@SpringBootTest
@ActiveProfiles("test")
class FeignContractTests {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deserializaEventoRealConLugarAnidado() {
        String json = """
                {
                  "id": 15,
                  "nombre": "Festival",
                  "lugar": {"id": 7, "nombre": "Estadio"},
                  "idOrganizador": 4,
                  "fechaHora": "2027-05-20T21:00:00",
                  "estado": "ACTIVO",
                  "estadoPublicacion": "PUBLICADO"
                }
                """;

        EventoFechaResponseDTO evento = objectMapper.readValue(json, EventoFechaResponseDTO.class);

        assertThat(evento.id()).isEqualTo(15L);
        assertThat(evento.estadoPublicacion()).isEqualTo("PUBLICADO");
        assertThat(evento.estado()).isEqualTo("ACTIVO");
        assertThat(evento.idLugar()).isEqualTo(7L);
        assertThat(evento.fechaHora()).isEqualTo(java.time.LocalDateTime.of(2027, 5, 20, 21, 0));
    }

    @Test
    void deserializaReservaRealIgnorandoCamposExtra() {
        String json = """
                {
                  "id": 55,
                  "claveIdempotencia": "clave-1",
                  "idCliente": 2,
                  "idEvento": 1,
                  "idSector": 3,
                  "cantidad": 2,
                  "precioUnitario": 1500.00,
                  "venceEn": "2026-11-10T15:00:00Z",
                  "estado": "CONFIRMADA",
                  "cantidadDevuelta": 1,
                  "idAsientos": [11, 12],
                  "idAsientosDevueltos": [11]
                }
                """;

        ReservaResponseDTO reserva = objectMapper.readValue(json, ReservaResponseDTO.class);

        assertThat(reserva.id()).isEqualTo(55L);
        assertThat(reserva.precioUnitario()).isEqualByComparingTo("1500.00");
        assertThat(reserva.estado()).isEqualTo("CONFIRMADA");
        assertThat(reserva.idAsientos()).containsExactly(11L, 12L);
    }

    @Test
    void serializaReservaRequestConLosNombresEsperados() {
        ReservaRequestDTO solicitud = new ReservaRequestDTO(
                "clave-1", 2L, 1L, 3L, 2, List.of(11L, 12L)
        );

        String json = objectMapper.writeValueAsString(solicitud);

        assertThat(json).contains("\"claveIdempotencia\":\"clave-1\"");
        assertThat(json).contains("\"idCliente\":2");
        assertThat(json).contains("\"idEvento\":1");
        assertThat(json).contains("\"idSector\":3");
        assertThat(json).contains("\"cantidad\":2");
        assertThat(json).contains("\"idAsientos\":[11,12]");
    }

    @Test
    void serializaDevolucionRequestConFechaInstant() {
        DevolucionRequestDTO devolucion = new DevolucionRequestDTO(
                "devolucion-1",
                1,
                Instant.parse("2026-01-01T10:00:00Z"),
                List.of(11L)
        );

        String json = objectMapper.writeValueAsString(devolucion);

        assertThat(json).contains("\"claveIdempotencia\":\"devolucion-1\"");
        assertThat(json).contains("\"cantidad\":1");
        assertThat(json).contains("\"solicitadaEn\":\"2026-01-01T10:00:00Z\"");
        assertThat(json).contains("\"idAsientos\":[11]");
    }
}
