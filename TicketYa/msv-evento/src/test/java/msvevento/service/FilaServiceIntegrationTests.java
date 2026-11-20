package msvevento.service;

import msvevento.dto.TurnoFilaResponse;
import msvevento.entity.EstadoEvento;
import msvevento.entity.EstadoPublicacion;
import msvevento.entity.Evento;
import msvevento.repository.EventoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "fila.max-compradores-simultaneos=2")
@ActiveProfiles("test")
@Testcontainers
class FilaServiceIntegrationTests {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired FilaService filaService;
    @Autowired EventoRepository eventoRepository;
    @Autowired StringRedisTemplate redis;

    private Long eventoId;

    @BeforeEach
    void preparar() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        eventoRepository.deleteAll();

        Evento evento = new Evento();
        evento.setNombre("Evento de alta demanda");
        evento.setEstado(EstadoEvento.EN_CURSO);
        evento.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);
        evento.setDemanda(true);
        eventoId = eventoRepository.saveAndFlush(evento).getId();
    }

    @Test
    void noAdmiteMasCompradoresSimultaneosQueElLimiteConfigurado() {
        TurnoFilaResponse primero = filaService.entrar(eventoId, 101L);
        TurnoFilaResponse segundo = filaService.entrar(eventoId, 102L);
        TurnoFilaResponse tercero = filaService.entrar(eventoId, 103L);

        assertThat(primero.estado()).isEqualTo("ADMITIDO");
        assertThat(segundo.estado()).isEqualTo("ADMITIDO");
        assertThat(tercero.estado()).isEqualTo("EN_ESPERA");
        assertThat(tercero.posicion()).isEqualTo(1);
        assertThat(tercero.autorizacionCompra()).isNull();
    }

    @Test
    void rechazaAutorizacionDeOtroClienteYTurnoVencido() {
        TurnoFilaResponse turno = filaService.entrar(eventoId, 201L);

        var turnoAjeno = filaService.validarAutorizacion(eventoId, 999L,
                turno.autorizacionCompra(), "compra-ajena");
        assertThat(turnoAjeno.autorizado()).isFalse();
        assertThat(turnoAjeno.motivo()).isEqualTo("TURNO_AJENO_O_INEXISTENTE");

        String keyTurno = "fila:evento:" + eventoId + ":turno:" + turno.turnoId();
        redis.opsForHash().put(keyTurno, "venceEn", Long.toString(System.currentTimeMillis() - 1));

        var turnoVencido = filaService.validarAutorizacion(eventoId, 201L,
                turno.autorizacionCompra(), "compra-201");
        assertThat(turnoVencido.autorizado()).isFalse();
        assertThat(turnoVencido.motivo()).isEqualTo("TURNO_VENCIDO");
    }

    @Test
    void liberaUnLugarAlCompletarLaCompraYAdmiteAlSiguiente() {
        TurnoFilaResponse primero = filaService.entrar(eventoId, 301L);
        filaService.entrar(eventoId, 302L);
        TurnoFilaResponse siguiente = filaService.entrar(eventoId, 303L);
        assertThat(siguiente.estado()).isEqualTo("EN_ESPERA");

        assertThat(filaService.validarAutorizacion(eventoId, 301L,
                primero.autorizacionCompra(), "compra-301").autorizado()).isTrue();
        assertThat(filaService.completarCompra(eventoId, 301L,
                primero.autorizacionCompra(), "compra-301").autorizado()).isTrue();

        assertThat(filaService.consultar(eventoId, 303L).estado()).isEqualTo("ADMITIDO");
    }
}
