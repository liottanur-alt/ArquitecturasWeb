
package msvevento.service;

import msvevento.dto.ArtistaResponseDTO;
import msvevento.dto.EventoRequestDTO;
import msvevento.dto.EventoResponseDTO;
import msvevento.dto.LugarResponseDTO;
import msvevento.entity.*;
import msvevento.repository.ArtistaRepository;
import msvevento.repository.EventoRepository;
import msvevento.repository.LugarRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class EventoService {

    private final EventoRepository eventoRepository;
    private final LugarRepository lugarRepository;
    private final ArtistaRepository artistaRepository;

    public EventoService(
            EventoRepository eventoRepository,
            LugarRepository lugarRepository,
            ArtistaRepository artistaRepository
    ) {
        this.eventoRepository = eventoRepository;
        this.lugarRepository = lugarRepository;
        this.artistaRepository = artistaRepository;
    }

    // Listar todos los eventos
    @Transactional(readOnly = true)
    public List<EventoResponseDTO> listarEventos() {

        return eventoRepository.findAll()
                .stream()
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

    // Crear un evento
    public EventoResponseDTO crearEvento(EventoRequestDTO dto) {

        Evento evento = new Evento();

        evento.setNombre(dto.getNombre());
        evento.setDescripcion(dto.getDescripcion());
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
    public EventoResponseDTO actualizarEvento(Long id, EventoRequestDTO dto) {

        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontro el evento con ID: " + id
                ));

        evento.setNombre(dto.getNombre());
        evento.setDescripcion(dto.getDescripcion());
        evento.setTipo(dto.getTipo());
        evento.setIdOrganizador(dto.getIdOrganizador());
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
    public EventoResponseDTO publicarEvento(Long id) {

        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontro el evento con ID: " + id
                ));

        if (evento.getEstadoPublicacion() != EstadoPublicacion.BORRADOR) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solo se pueden publicar eventos en estado BORRADOR"
            );
        }



        evento.setEstadoPublicacion(EstadoPublicacion.PUBLICADO);

        Evento eventoGuardado = eventoRepository.save(evento);

        return convertirAResponseDTO(eventoGuardado);
    }


    // Suspender un evento
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
    public EventoResponseDTO cancelarEvento(Long id) {

        Evento evento = eventoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontro el evento con ID: " + id
                ));

        if (evento.getEstado() == EstadoEvento.CANCELADO) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El evento ya se encuentra cancelado"
            );
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

        return convertirAResponseDTO(eventoGuardado);
    }
    // Eliminar un evento
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
    public List<EventoResponseDTO> buscarEventos(
            TipoEvento tipo,
            Long idOrganizador,
            Long idParticipante,
            String ciudad,
            LocalDateTime fechaDesde,
            LocalDateTime fechaHasta,
            int pagina,
            int tamanio,
            String ordenar
    ) {

        // Validar el campo por el que se quiere ordenar
        String[] partesOrden = ordenar.split(",");

        String campoOrden = partesOrden[0].trim();

        if (!List.of("fechaHora", "nombre").contains(campoOrden)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Campo de ordenamiento no valido"
            );
        }

        // Determinar la dirección del ordenamiento
        Sort.Direction direccion = Sort.Direction.ASC;

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

        // Crear la configuración de paginación y ordenamiento
        Pageable pageable = PageRequest.of(
                pagina,
                tamanio,
                Sort.by(direccion, campoOrden)
        );

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
                evento.isDemanda()
        );
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
