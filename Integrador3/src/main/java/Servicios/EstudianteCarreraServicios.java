package Servicios;
import Entities.EstudianteCarrera;
import Entities.EstudianteCarreraPK;
import Repository.EstudianteCarreraRepository;
import Servicios.DTO.EstudianteCarrera.Res.EstudianteCarreraResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import Servicios.exception.ExceptionDTO;
import java.util.List;
@Service
@RequiredArgsConstructor
public class EstudianteCarreraServicios {

        private final EstudianteCarreraRepository estudianteCarreraRepository;
        @Transactional(readOnly = true) // indica que la operación es de lectura.
        // devuelve todas las inscripciones en formato DTO.
        public List<EstudianteCarreraResponseDTO> findAll() {
            return estudianteCarreraRepository.findAll()
                    .stream()
                    .map(EstudianteCarreraResponseDTO::new)
                    .toList();
        }

        @Transactional(readOnly = true) // indica que la operación es de lectura.
        //busca una inscripción usando EstudianteCarreraPK, que contiene el ID del estudiante y el ID de la carrera.
        public EstudianteCarreraResponseDTO findById(EstudianteCarreraPK id) {
            return this.estudianteCarreraRepository.findById(id)
                    .map(EstudianteCarreraResponseDTO::new)
                    .orElseThrow(() ->
                            new ExceptionDTO("EstudianteCarrera", id));
        }


}
