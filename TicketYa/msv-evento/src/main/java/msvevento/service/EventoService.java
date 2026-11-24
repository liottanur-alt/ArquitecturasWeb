
package msvevento.service;

import feign.FeignException;
import msvevento.client.SectorEventoClient;
import msvevento.dto.ArtistaResponseDTO;
import msvevento.dto.EventoRequestDTO;
import msvevento.dto.EventoResponseDTO;
import msvevento.dto.EventosPorIdsRequest;
import msvevento.dto.LugarResponseDTO;
import msvevento.dto.SectorEventoResponseDTO;
import msvevento.entity.*;
import msvevento.repository.ArtistaRepository;
import msvevento.repository.EventoRepository;
import msvevento.repository.LugarRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class EventoService {

    private final EventoRepository eventoRepository;
    private final LugarRepository lugarRepository;
    private final ArtistaRepository artistaRepository;
    private final SectorEventoClient sectorEventoClient;
    private final ApplicationEventPublisher eventos;

    public EventoService(
            EventoRepository eventoRepository,
            LugarRepository lugarRepository,
            ArtistaRepository artistaRepository,
            SectorEventoClient sectorEventoClient,
            ApplicationEventPublisher eventos
    ) {
        this.eventoRepository = eventoRepository;
        this.lugarRepository = lugarRepository;
        this.artistaRepository = artistaRepository;
        this.sectorEventoClient = sectorEventoClient;
        this.eventos = eventos;
    }

    // Listar todos los eventos
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "eventos-catalogo")
    public List<EventoResponseDTO> listarEventosPublicos() {
        return eventoRepository.findByEstadoPublicacion(EstadoPublicacion.PUBLICADO)
                .stream()
                .map(this::convertirAResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventoResponseDTO> listarEventosDelOrganizador(Long idOrganizador) {
        return eventoRepository.findByIdOrganizador(idOrganizador).stream()
                .map(this::convertirAResponseDTO)
                .toList();
    }

    // Buscar un evento por ID
    @Transactional(readOnly = true)
    public EventoResponseDTO buscarEventoPorId(Long id) {

        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontro el evento con ID: " + id
                ));

        return convertirAResponseDTO(evento);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "evento-detalle", key = "#p0")
    public EventoResponseDTO buscarEventoPublicado(Long id) {
        Evento evento = eventoRepository.findByIdAndEstadoPublicacion(id, EstadoPublicacion.PUBLICADO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento publicado no encontrado"));
        return convertirAResponseDTO(evento);
    }

    // Crear un evento
    public EventoResponseDTO crearEvento(EventoRequestDTO dto) {

        Evento evento = new Evento();

        evento.setNombre(dto.getNombre());
        evento.setDescripcion(dto.getDescripcion());
        evento.setImagenUrl(normalizarImagen(dto.getImagenUrl()));
        evento.setTipo(dto.getTipo());
        evento.setIdOrganizador(dto.getIdOrganizador());
        evento.setFechaHora(dto.getFechaHora());
        evento.setEstado(EstadoEvento.EN_CURSO);
        evento.setEstadoPublicacion(EstadoPublicacion.BORRADOR);
        evento.setDemanda(dto.isDemanda());

        // Buscar y asociar el lugar
        Lugar lugar = lugarRepository.findById(dto.getIdLugar())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontro el lugar con ID: " + dto.getIdLugar()
                ));

        evento.setLugar(lugar);

        // Buscar y asociar los artistas participantes
        List<Artista> participantes = new ArrayList<>();

        if (dto.getParticipantesIds() != null) {

            for (Long idArtista : dto.getParticipantesIds()) {

                Artista artista = artistaRepository.findById(idArtista)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "No se encontro el artista con ID: " + idArtista
                        ));

                participantes.add(artista);
            }
        }

        evento.setParticipantes(participantes);

        Evento eventoGuardado = eventoRepository.save(evento);

        return convertirAResponseDTO(eventoGuardado);
    }

    // Actualizar un evento
    public EventoResponseDTO actualizarEvento(Long id, Long idOrganizador, EventoRequestDTO dto) {

        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontro el evento con ID: " + id
                ));

        validarOrganizador(evento, idOrganizador);
        if (evento.getEstadoPublicacion() != EstadoPublicacion.BORRADOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se pueden editar borradores");
        }

        evento.setNombre(dto.getNombre());
        evento.setDescripcion(dto.getDescripcion());
        if (dto.getImagenUrl() != null) {
            evento.setImagenUrl(normalizarImagen(dto.getImagenUrl()));
        }
        evento.setTipo(dto.getTipo());
        evento.setFechaHora(dto.getFechaHora());
        evento.setDemanda(dto.isDemanda());

        // Buscar y actualizar el lugar
        Lugar lugar = lugarRepository.findById(dto.getIdLugar())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontro el lugar con ID: " + dto.getIdLugar()
                ));

        evento.setLugar(lugar);

        // Buscar y actualizar los artistas participantes
        List<Artista> participantes = new ArrayList<>();

        if (dto.getParticipantesIds() != null) {

            for (Long idArtista : dto.getParticipantesIds()) {

                Artista artista = artistaRepository.findById(idArtista)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "No se encontro el artista con ID: " + idArtista
                        ));

                participantes.add(artista);
            }
        }

        evento.setParticipantes(participantes);

        Evento eventoActualizado = eventoRepository.save(evento);

        return convertirAResponseDTO(eventoActualizado);
    }
    // Publicar un evento
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "eventos-catalogo", allEntries = true),
            @CacheEvict(cacheNames = "eventos-busqueda", allEntries = true),
            @CacheEvict(cacheNames = "evento-detalle", key = "#p0")
    })
    public EventoResponseDTO publicarEvento(Long id, Long idOrganizador) {

        Evento evento = eventoRepository.buscarParaActualizar(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontro el evento con ID: " + id
                ));

        validarOrganizador(evento, idOrganizador);

        if (evento.getEstadoPublicacion() != EstadoPublicacion.BORRADOR) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solo se pueden publicar eventos en estado BORRADOR"
            );
        }

        validarDatosParaPublicar(evento);
        validarSectoresParaPublicar(id);

        evento.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);

        Evento eventoGuardado = eventoRepository.save(evento);
        eventos.publishEvent(new NotificarEvento.Publicado(eventoGuardado.getId(), eventoGuardado.getNombre(),
                eventoGuardado.getLugar().getCiudad(), eventoGuardado.getFechaHora(),
                eventoGuardado.getParticipantes().stream().map(Artista::getId).toList()));

        return convertirAResponseDTO(eventoGuardado);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "eventos-catalogo", allEntries = true),
            @CacheEvict(cacheNames = "eventos-busqueda", allEntries = true),
            @CacheEvict(cacheNames = "evento-detalle", key = "#p0")
    })
    public EventoResponseDTO reprogramarEvento(Long id, Long idOrganizador, LocalDateTime fechaHora, Long idLugar) {
        Evento evento = eventoRepository.buscarParaActualizar(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento inexistente"));
        validarOrganizador(evento, idOrganizador);
        if (evento.getEstadoPublicacion() != EstadoPublicacion.PUBLICADO
                || evento.getEstado() == EstadoEvento.CANCELADO || evento.getEstado() == EstadoEvento.FINALIZADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se pueden reprogramar eventos publicados activos");
        }
        if (fechaHora == null && idLugar == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indicá fecha y hora o lugar");
        }
        Lugar lugarAnterior = evento.getLugar();
        LocalDateTime fechaAnterior = evento.getFechaHora();
        Lugar lugar = idLugar == null ? lugarAnterior : lugarRepository.findById(idLugar)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lugar inexistente"));
        LocalDateTime fecha = fechaHora == null ? fechaAnterior : fechaHora;
        if (Objects.equals(fechaAnterior, fecha) && Objects.equals(lugarAnterior.getId(), lugar.getId())) {
            return convertirAResponseDTO(evento);
        }
        if (fecha == null || !fecha.isAfter(LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva programación debe ser futura");
        }
        evento.setFechaHora(fecha);
        evento.setLugar(lugar);
        Evento guardado = eventoRepository.save(evento);
        String detalle = "Evento: " + evento.getNombre() + ". Antes: " + NotificarEvento.fecha(fechaAnterior)
                + " en " + lugarAnterior.getNombre() + ". Ahora: " + NotificarEvento.fecha(fecha)
                + " en " + lugar.getNombre() + ".";
        eventos.publishEvent(new NotificarEvento.Cambio(id, "evento-modificado-" + UUID.randomUUID(),
                "EVENTO_MODIFICADO", detalle));
        return convertirAResponseDTO(guardado);
    }

    private void validarOrganizador(Evento evento, Long idOrganizador) {
        if (idOrganizador == null || !idOrganizador.equals(evento.getIdOrganizador())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No sos el organizador de este evento");
        }
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "eventos-catalogo", allEntries = true),
            @CacheEvict(cacheNames = "eventos-busqueda", allEntries = true),
            @CacheEvict(cacheNames = "evento-detalle", key = "#p0")
    })
    public void actualizarAgotamiento(Long id, boolean agotado) {
        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado"));
        if (evento.getEstado() == EstadoEvento.CANCELADO || evento.getEstado() == EstadoEvento.FINALIZADO) return;
        if (agotado) {
            evento.setEstado(EstadoEvento.AGOTADO);
        } else if (evento.getEstado() == EstadoEvento.AGOTADO
                && evento.getEstadoPublicacion() == EstadoPublicacion.PUBLICADO
                && evento.getFechaHora() != null
                && evento.getFechaHora().isAfter(LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")))) {
            evento.setEstado(EstadoEvento.EN_CURSO);
        }
        eventoRepository.save(evento);
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "eventos-catalogo", allEntries = true),
            @CacheEvict(cacheNames = "eventos-busqueda", allEntries = true),
            @CacheEvict(cacheNames = "evento-detalle", key = "#p0")
    })
    public EventoResponseDTO finalizarEvento(Long id, Long idOrganizador) {
        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado"));
        validarOrganizador(evento, idOrganizador);
        if (evento.getFechaHora() == null || evento.getFechaHora().isAfter(
                LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El evento todavía no llegó a su fecha y hora");
        }
        if (evento.getEstadoPublicacion() != EstadoPublicacion.PUBLICADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se pueden finalizar eventos publicados");
        }
        if (evento.getEstado() == EstadoEvento.CANCELADO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede finalizar un evento cancelado");
        }
        evento.setEstado(EstadoEvento.FINALIZADO);
        return convertirAResponseDTO(eventoRepository.save(evento));
    }

    private void validarDatosParaPublicar(Evento evento) {
        List<String> faltantes = new ArrayList<>();
        if (evento.getNombre() == null || evento.getNombre().isBlank()) faltantes.add("nombre");
        if (evento.getDescripcion() == null || evento.getDescripcion().isBlank()) faltantes.add("descripción");
        if (evento.getTipo() == null) faltantes.add("tipo");
        if (evento.getIdOrganizador() == null || evento.getIdOrganizador() <= 0) faltantes.add("organizador válido");
        if (evento.getFechaHora() == null
                || !evento.getFechaHora().isAfter(LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")))) {
            faltantes.add("fecha y hora futuras");
        }
        if (evento.getLugar() == null) {
            faltantes.add("lugar");
        } else {
            Lugar lugar = evento.getLugar();
            if (lugar.getNombre() == null || lugar.getNombre().isBlank()) faltantes.add("nombre del lugar");
            if (lugar.getDireccion() == null || lugar.getDireccion().isBlank()) faltantes.add("dirección del lugar");
            if (lugar.getLatitud() == null || lugar.getLongitud() == null) faltantes.add("coordenadas del lugar");
        }
        if (evento.getParticipantes() == null || evento.getParticipantes().isEmpty()) {
            faltantes.add("al menos un participante");
        }

        if (!faltantes.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede publicar. Completá: " + String.join(", ", faltantes)
            );
        }
    }

    private void validarSectoresParaPublicar(Long idEvento) {
        List<SectorEventoResponseDTO> sectores;
        try {
            sectores = sectorEventoClient.listarConfigurados(idEvento);
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Configurá al menos un sector con precio no negativo y capacidad positiva antes de publicar"
            );
        } catch (FeignException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo validar la configuración de sectores. Verificá que SectorAsiento esté disponible"
            );
        }

        if (sectores == null || sectores.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Configurá al menos un sector con precio no negativo y capacidad positiva antes de publicar"
            );
        }

        boolean haySectorValido = sectores.stream().anyMatch(sector ->
                sector != null
                        && sector.precio() != null && sector.precio().signum() >= 0
                        && sector.capacidad() != null && sector.capacidad() > 0
        );
        if (!haySectorValido) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ningún sector es válido. Configurá al menos uno con precio no negativo y capacidad positiva"
            );
        }
    }


    // Suspender un evento
    @Caching(evict = {
            @CacheEvict(cacheNames = "eventos-catalogo", allEntries = true),
            @CacheEvict(cacheNames = "eventos-busqueda", allEntries = true),
            @CacheEvict(cacheNames = "evento-detalle", key = "#p0")
    })
    public EventoResponseDTO suspenderEvento(Long id) {

        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontro el evento con ID: " + id
                ));

        if (evento.getEstadoPublicacion() != EstadoPublicacion.PUBLICADO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solo se pueden suspender eventos publicados"
            );
        }

        evento.setEstadoPublicacion(EstadoPublicacion.SUSPENDIDO);

        Evento eventoGuardado = eventoRepository.save(evento);

        return convertirAResponseDTO(eventoGuardado);
    }


    // Cancelar un evento
    @Caching(evict = {
            @CacheEvict(cacheNames = "eventos-catalogo", allEntries = true),
            @CacheEvict(cacheNames = "eventos-busqueda", allEntries = true),
            @CacheEvict(cacheNames = "evento-detalle", key = "#p0")
    })
    public EventoResponseDTO cancelarEvento(Long id) {

        Evento evento = eventoRepository.buscarParaActualizar(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontro el evento con ID: " + id
                ));

        if (evento.getEstado() == EstadoEvento.CANCELADO) {
            return convertirAResponseDTO(evento);
        }

        if (evento.getEstado() == EstadoEvento.FINALIZADO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No se puede cancelar un evento finalizado"
            );
        }

        evento.setEstado(EstadoEvento.CANCELADO);
        evento.setEstadoPublicacion(EstadoPublicacion.SUSPENDIDO);

        Evento eventoGuardado = eventoRepository.save(evento);

        eventos.publishEvent(new NotificarEvento.Cambio(id, "evento-cancelado-" + id,
                "EVENTO_CANCELADO", "Evento: " + eventoGuardado.getNombre() + "."));
        return convertirAResponseDTO(eventoGuardado);
    }
    // Eliminar un evento
    @Caching(evict = {
            @CacheEvict(cacheNames = "eventos-catalogo", allEntries = true),
            @CacheEvict(cacheNames = "eventos-busqueda", allEntries = true),
            @CacheEvict(cacheNames = "evento-detalle", key = "#p0")
    })
    public void eliminarEvento(Long id) {

        if (!eventoRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No se encontro el evento con ID: " + id
            );
        }

        eventoRepository.deleteById(id);
    }

    // Buscar eventos aplicando filtros
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "eventos-busqueda", condition = "!#p8.trim().startsWith('precio')")
    public List<EventoResponseDTO> buscarEventos(
            TipoEvento tipo,
            Long idOrganizador,
            Long idParticipante,
            String ciudad,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta,
            int pagina,
            int tamanio,
            String ordenar,
            String texto
    ) {

        // Validar el campo por el que se quiere ordenar
        String[] partesOrden = ordenar.split(",");

        String campoOrden = partesOrden[0].trim();

        if (!List.of("fechaHora", "nombre", "precio", "relevancia").contains(campoOrden)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Campo de ordenamiento no valido"
            );
        }

        // Determinar la dirección del ordenamiento
        Sort.Direction direccion = "relevancia".equals(campoOrden) ? Sort.Direction.DESC : Sort.Direction.ASC;

        if (partesOrden.length > 1) {
            try {
                direccion = Sort.Direction.fromString(
                        partesOrden[1].trim()
                );
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Direccion de ordenamiento no valida"
                );
            }
        }

        String consulta = CiudadNormalizada.de(texto);
        PageRequest paginaSolicitada = PageRequest.of(pagina, tamanio);
        if (!consulta.isEmpty() || "precio".equals(campoOrden) || "relevancia".equals(campoOrden)) {
            var candidatos = eventoRepository.buscarConFiltros(tipo, idOrganizador, idParticipante,
                    ciudad, fechaDesde, fechaHasta, Pageable.unpaged()).getContent();
            return ordenarBusqueda(candidatos, consulta, campoOrden, direccion, paginaSolicitada);
        }
        Pageable pageable = paginaSolicitada.withSort(Sort.by(direccion, campoOrden).and(Sort.by("id")));

        // Buscar eventos en el repositorio
        Page<Evento> eventos = eventoRepository.buscarConFiltros(
                tipo,
                idOrganizador,
                idParticipante,
                ciudad,
                fechaDesde,
                fechaHasta,
                pageable
        );

        // Convertir los resultados a DTO
        return eventos.getContent()
                .stream()
                .map(this::convertirAResponseDTO)
                .toList();
    }

    private List<EventoResponseDTO> ordenarBusqueda(List<Evento> candidatos, String consulta, String campo,
                                                     Sort.Direction direccion, Pageable pagina) {
        var coincidencias = candidatos.stream()
                .map(evento -> new Coincidencia(evento, relevancia(evento, consulta)))
                .filter(resultado -> consulta.isEmpty() || resultado.relevancia() > 0)
                .toList();
        if (coincidencias.isEmpty()) {
            return List.of();
        }

        Comparator<Coincidencia> orden;
        if ("precio".equals(campo)) {
            Map<Long, BigDecimal> precios;
            try {
                precios = sectorEventoClient.preciosMinimos(new EventosPorIdsRequest(
                        coincidencias.stream().map(resultado -> resultado.evento().getId()).toList()));
            } catch (FeignException exception) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudieron consultar los precios");
            }
            Comparator<BigDecimal> ordenPrecio = direccion.isAscending()
                    ? Comparator.naturalOrder() : Comparator.reverseOrder();
            orden = Comparator.comparing(resultado -> precios.get(resultado.evento().getId()),
                    Comparator.nullsLast(ordenPrecio));
        } else {
            orden = switch (campo) {
                case "nombre" -> Comparator.comparing(resultado -> resultado.evento().getNombre());
                case "fechaHora" -> Comparator.comparing(resultado -> resultado.evento().getFechaHora());
                default -> Comparator.comparingInt(Coincidencia::relevancia);
            };
            if (direccion.isDescending()) {
                orden = orden.reversed();
            }
        }

        return coincidencias.stream()
                .sorted(orden.thenComparing(resultado -> resultado.evento().getFechaHora())
                        .thenComparing(resultado -> resultado.evento().getId()))
                .skip(pagina.getOffset())
                .limit(pagina.getPageSize())
                .map(resultado -> convertirAResponseDTO(resultado.evento()))
                .toList();
    }

    private int relevancia(Evento evento, String consulta) {
        if (consulta.isEmpty()) return 0;
        String nombre = CiudadNormalizada.de(evento.getNombre());
        if (nombre.equals(consulta)) return 5;
        if (nombre.startsWith(consulta)) return 4;
        if (nombre.contains(consulta)) return 3;
        if (evento.getParticipantes().stream().anyMatch(
                artista -> CiudadNormalizada.de(artista.getNombre()).contains(consulta))) return 2;
        var lugar = evento.getLugar();
        if (lugar != null && (CiudadNormalizada.de(lugar.getNombre()).contains(consulta)
                || CiudadNormalizada.de(lugar.getCiudad()).contains(consulta))) return 1;
        return CiudadNormalizada.de(evento.getDescripcion()).contains(consulta) ? 1 : 0;
    }

    private record Coincidencia(Evento evento, int relevancia) {}

    // Convertir entidad a ResponseDTO
    private EventoResponseDTO convertirAResponseDTO(Evento evento) {

        List<ArtistaResponseDTO> participantes = evento.getParticipantes()
                .stream()
                .map(this::convertirArtistaAResponseDTO)
                .toList();

        LugarResponseDTO lugar = convertirLugarAResponseDTO(evento.getLugar());

        return new EventoResponseDTO(
                evento.getId(),
                evento.getNombre(),
                evento.getDescripcion(),
                evento.getTipo(),
                evento.getIdOrganizador(),
                participantes,
                evento.getFechaHora(),
                lugar,
                evento.getEstado(),
                evento.getEstadoPublicacion(),
                evento.isDemanda(),
                evento.getImagenUrl()
        );
    }

    private String normalizarImagen(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String ruta = valor.strip();
        if (ruta.length() > 2048 || ruta.indexOf('\\') >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL de imagen inválida");
        }
        try {
            URI uri = URI.create(ruta);
            boolean local = ruta.startsWith("/") && !ruta.startsWith("//");
            boolean web = ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
            if (local || web) {
                return ruta;
            }
        } catch (IllegalArgumentException ignored) {
            // URI mal formada.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL de imagen inválida");
    }

    // Convertir Artista a ArtistaResponseDTO
    private ArtistaResponseDTO convertirArtistaAResponseDTO(Artista artista) {

        return new ArtistaResponseDTO(
                artista.getId(),
                artista.getNombre(),
                artista.getGenero()
        );
    }

    // Convertir Lugar a LugarResponseDTO
    private LugarResponseDTO convertirLugarAResponseDTO(Lugar lugar) {

        return new LugarResponseDTO(
                lugar.getId(),
                lugar.getNombre(),
                lugar.getDireccion(),
                lugar.getLatitud(),
                lugar.getLongitud(),
                lugar.getCiudad(),
                lugar.getTipo()
        );
    }
}
