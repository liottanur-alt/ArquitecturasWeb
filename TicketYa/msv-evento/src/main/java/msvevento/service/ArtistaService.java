package msvevento.service;

import msvevento.dto.ArtistaRequestDTO;
import msvevento.dto.ArtistaResponseDTO;
import msvevento.entity.Artista;
import msvevento.repository.ArtistaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service // Le indica a Spring que la clase contiene lógica de negocio
// y que debe administrarla como un componente de la aplicación.
@Transactional
public class ArtistaService {
    private  final ArtistaRepository artistaRepository;

    public ArtistaService(ArtistaRepository artistaRepository){
        this.artistaRepository = artistaRepository;
        //para poder acceder a la BD
    }
    //LISTAR TODOS LOS ARTISTAS
    @Transactional(readOnly = true) // indica que los metodos trabajn dentro de una transaccion
    public List<ArtistaResponseDTO> listarArtistas() {

        return artistaRepository.findAll()
                .stream()
                .map(this::convertirAResponseDTO)
                .toList();
    }

    // BUSCAR UN ARTISTA POR ID
    @Transactional(readOnly = true)
    public ArtistaResponseDTO buscarArtistaPorId(Long id) {

        Artista artista = artistaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontro el artista con ID: " + id
                ));

        return convertirAResponseDTO(artista);
    }

    //CREAR UN ARTISTA
    public ArtistaResponseDTO crearArtista(ArtistaRequestDTO dto) {

        Artista artista = new Artista();

        artista.setNombre(dto.getNombre());
        artista.setGenero(dto.getGenero());

        Artista artistaGuardado = artistaRepository.save(artista);

        return convertirAResponseDTO(artistaGuardado);
    }

    // ACTUALIZAR UN ARTISTA
    public ArtistaResponseDTO actualizarArtista(Long id, ArtistaRequestDTO dto) {

        Artista artista = artistaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontro el artista con ID: " + id
                ));

        artista.setNombre(dto.getNombre());
        artista.setGenero(dto.getGenero());

        Artista artistaActualizado = artistaRepository.save(artista);

        return convertirAResponseDTO(artistaActualizado);
    }

    // ELIMINAR UN ARTISTA
    public void eliminarArtista(Long id) {

        if (!artistaRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No se encontro el artista con ID: " + id
            );
        }

        artistaRepository.deleteById(id);
    }

    // CONVERTIR ENTIDAD A RESPONSEDTO
    private ArtistaResponseDTO convertirAResponseDTO(Artista artista) {

        return new ArtistaResponseDTO(
                artista.getId(),
                artista.getNombre(),
                artista.getGenero()
        );
    }
}

