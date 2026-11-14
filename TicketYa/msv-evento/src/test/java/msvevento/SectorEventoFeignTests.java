package msvevento;

import com.sun.net.httpserver.HttpServer;
import msvevento.client.SectorEventoClient;
import msvevento.entity.*;
import msvevento.repository.*;
import msvevento.service.EventoService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class SectorEventoFeignTests {
    static final HttpServer REMOTO = iniciarProveedor();
    static volatile int estado;
    static volatile String respuesta;
    static volatile String ruta;
    @Autowired SectorEventoClient cliente;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("msvc.sectores.url", () -> "http://127.0.0.1:" + REMOTO.getAddress().getPort());
    }

    static HttpServer iniciarProveedor() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/eventos/", exchange -> {
                ruta = exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath();
                byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(estado, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @BeforeEach
    void configurar() {
        estado = 200;
        // Incluye campos adicionales del contrato real de DisponibilidadResponse.
        respuesta = """
                [{"idEvento":7,"idSector":2,"tipo":"CAMPO","precio":1500.50,
                  "capacidad":20,"disponibles":20,"reservados":0,"vendidos":0,
                  "cancelado":false,"asientos":[]}]
                """;
    }

    @AfterAll
    static void detener() { REMOTO.stop(0); }

    EventoService servicio(Evento evento) {
        var repositorio = mock(EventoRepository.class);
        when(repositorio.findById(7L)).thenReturn(Optional.of(evento));
        when(repositorio.save(evento)).thenReturn(evento);
        return new EventoService(repositorio, mock(LugarRepository.class), mock(ArtistaRepository.class), cliente);
    }

    Evento borrador() {
        var lugar = new Lugar();
        lugar.setId(1L); lugar.setNombre("Teatro"); lugar.setDireccion("Centro");
        lugar.setLatitud(-37.0); lugar.setLongitud(-59.0);
        var evento = new Evento();
        evento.setId(7L); evento.setNombre("Recital"); evento.setDescripcion("En vivo");
        evento.setTipo(TipoEvento.values()[0]); evento.setIdOrganizador(3L);
        evento.setLugar(lugar); evento.setParticipantes(List.of(new Artista()));
        evento.setFechaHora(LocalDateTime.now().plusYears(1));
        evento.setEstadoPublicacion(EstadoPublicacion.BORRADOR);
        evento.setEstado(EstadoEvento.EN_CURSO);
        return evento;
    }

    @Test
    void publicaConsultandoSectoresPorHttpConFeign() {
        var evento = borrador();
        servicio(evento).publicarEvento(7L, 3L);
        assertThat(ruta).isEqualTo("GET /eventos/7/sectores");
        assertThat(evento.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.PUBLICADO);
        var sectores = cliente.listarConfigurados(7L);
        assertThat(sectores.getFirst().precio()).isEqualByComparingTo("1500.50");
        assertThat(sectores.getFirst().capacidad()).isEqualTo(20);
    }

    @Test
    void noPublicaSinSectores() {
        respuesta = "[]";
        comprobarRechazo(409);
    }

    @Test
    void inventarioInexistenteDevuelveConflicto() {
        estado = 404; respuesta = "{}";
        comprobarRechazo(409);
    }

    @Test
    void proveedorCaidoNoPermitePublicar() {
        estado = 503; respuesta = "{}";
        comprobarRechazo(503);
    }

    void comprobarRechazo(int codigo) {
        var evento = borrador();
        assertThatThrownBy(() -> servicio(evento).publicarEvento(7L, 3L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(codigo));
        assertThat(evento.getEstadoPublicacion()).isEqualTo(EstadoPublicacion.BORRADOR);
    }
}
