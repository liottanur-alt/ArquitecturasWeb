package msvevento.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import msvevento.entity.EstadoEvento;
import msvevento.entity.EstadoPublicacion;
import msvevento.entity.Evento;
import msvevento.entity.Lugar;
import msvevento.entity.TipoEvento;
import msvevento.entity.TipoLugar;
import msvevento.repository.EventoRepository;
import msvevento.repository.LugarRepository;
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

@SpringBootTest(properties = {
        "spring.cache.type=redis",
        "spring.cache.redis.time-to-live=1500ms"
})
@ActiveProfiles("test")
@Testcontainers
class EventoCacheIntegrationTests {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired EventoService eventoService;
    @Autowired EventoRepository eventoRepository;
    @Autowired LugarRepository lugarRepository;
    @Autowired StringRedisTemplate redis;

    private Long eventoId;

    @BeforeEach
    void prepararEventoPublicado() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        eventoRepository.deleteAll();
        lugarRepository.deleteAll();

        Lugar lugar = new Lugar();
        lugar.setNombre("Teatro de prueba");
        lugar.setDireccion("Calle 123");
        lugar.setCiudad("Tandil");
        lugar.setLatitud(-37.3288);
        lugar.setLongitud(-59.1367);
        lugar.setTipo(TipoLugar.TEATRO);
        Lugar lugarGuardado = lugarRepository.saveAndFlush(lugar);

        Evento evento = new Evento();
        evento.setNombre("Evento original");
        evento.setDescripcion("Evento para probar la caché");
        evento.setTipo(TipoEvento.RECITAL);
        evento.setIdOrganizador(10L);
        evento.setFechaHora(LocalDateTime.now().plusDays(30));
        evento.setEstado(EstadoEvento.EN_CURSO);
        evento.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);
        evento.setLugar(lugarGuardado);
        evento.setParticipantes(List.of());
        eventoId = eventoRepository.saveAndFlush(evento).getId();
    }

    @Test
    void reutilizaLaRespuestaYLaActualizaCuandoVenceElTtl() throws InterruptedException {
        var primeraLectura = eventoService.listarEventosPublicos();
        assertThat(primeraLectura).extracting("nombre").containsExactly("Evento original");

        actualizarNombreDirectamenteEnBase("Evento actualizado en base");

        var mientrasSigueEnCache = eventoService.listarEventosPublicos();
        assertThat(mientrasSigueEnCache).extracting("nombre").containsExactly("Evento original");

        Thread.sleep(2_000);

        var despuesDelVencimiento = eventoService.listarEventosPublicos();
        assertThat(despuesDelVencimiento).extracting("nombre").containsExactly("Evento actualizado en base");
    }

    @Test
    void losCambiosDeEstadoInvalidanCatalogoBusquedaYDetalle() {
        eventoService.listarEventosPublicos();
        eventoService.buscarEventoPublicado(eventoId);
        eventoService.buscarEventos(null, null, null, null, null, null, 0, 10, "fechaHora,asc");

        eventoService.actualizarAgotamiento(eventoId, true);

        assertThat(eventoService.listarEventosPublicos())
                .singleElement().extracting(evento -> evento.getEstado().name()).isEqualTo("AGOTADO");
        assertThat(eventoService.buscarEventoPublicado(eventoId).getEstado()).isEqualTo(EstadoEvento.AGOTADO);
        assertThat(eventoService.buscarEventos(null, null, null, null, null, null, 0, 10, "fechaHora,asc"))
                .singleElement().extracting(evento -> evento.getEstado().name()).isEqualTo("AGOTADO");
    }

    private void actualizarNombreDirectamenteEnBase(String nombre) {
        Evento evento = eventoRepository.findById(eventoId).orElseThrow();
        evento.setNombre(nombre);
        eventoRepository.saveAndFlush(evento);
    }
}
