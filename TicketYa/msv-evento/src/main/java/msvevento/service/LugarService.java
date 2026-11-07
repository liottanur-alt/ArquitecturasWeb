
package msvevento.service;

import msvevento.dto.LugarRequestDTO;
import msvevento.dto.LugarResponseDTO;
import msvevento.entity.Lugar;
import msvevento.repository.LugarRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class LugarService {

    private final LugarRepository lugarRepository;

    public LugarService(LugarRepository lugarRepository) {
        this.lugarRepository = lugarRepository;
    }

    // Listar todos los lugares
    @Transactional(readOnly = true)
    public List<LugarResponseDTO> listarLugares() {

        return lugarRepository.findAll()
                .stream()
                .map(this::convertirAResponseDTO)
                .toList();
    }

    // Buscar un lugar por ID
    @Transactional(readOnly = true)
    public LugarResponseDTO buscarLugarPorId(Long id) {

        Lugar lugar = lugarRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontro el lugar con ID: " + id
                ));

        return convertirAResponseDTO(lugar);
    }

    // Crear un lugar
    public LugarResponseDTO crearLugar(LugarRequestDTO dto) {

        Lugar lugar = new Lugar();

        lugar.setNombre(dto.getNombre());
        lugar.setDireccion(dto.getDireccion());
        lugar.setLatitud(dto.getLatitud());
        lugar.setLongitud(dto.getLongitud());
        lugar.setCiudad(dto.getCiudad());
        lugar.setTipo(dto.getTipo());

        Lugar lugarGuardado = lugarRepository.save(lugar);

        return convertirAResponseDTO(lugarGuardado);
    }

    // Actualizar un lugar
    public LugarResponseDTO actualizarLugar(Long id, LugarRequestDTO dto) {

        Lugar lugar = lugarRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontro el lugar con ID: " + id
                ));

        lugar.setNombre(dto.getNombre());
        lugar.setDireccion(dto.getDireccion());
        lugar.setLatitud(dto.getLatitud());
        lugar.setLongitud(dto.getLongitud());
        lugar.setCiudad(dto.getCiudad());
        lugar.setTipo(dto.getTipo());

        Lugar lugarActualizado = lugarRepository.save(lugar);

        return convertirAResponseDTO(lugarActualizado);
    }

    // Eliminar un lugar
    public void eliminarLugar(Long id) {

        if (!lugarRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No se encontro el lugar con ID: " + id
            );
        }

        lugarRepository.deleteById(id);
    }

    // Convertir entidad a ResponseDTO
    private LugarResponseDTO convertirAResponseDTO(Lugar lugar) {

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
