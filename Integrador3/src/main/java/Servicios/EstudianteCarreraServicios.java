 package Servicios;

import Entities.Carrera;
import Entities.Estudiante;
import Entities.EstudianteCarrera;
import Entities.EstudianteCarreraPK;
import Repository.MySql.MySQLEstudianteCarreraRepository;
import Repository.MySql.MySqlCarreraRepository;
import Repository.MySql.MySqlEstudianteRepository;
import Servicios.DTO.EstudianteCarrera.Req.EstudianteCarreraDTOReq;
import Servicios.DTO.EstudianteCarrera.Res.EstudianteCarreraDTORes;
import Servicios.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EstudianteCarreraServicios {

    private final MySQLEstudianteCarreraRepository estudianteCarreraRepository;
    private final MySqlEstudianteRepository estudianteRepository;
    private final MySqlCarreraRepository carreraRepository;

    @Transactional
    public EstudianteCarreraDTORes matricular(
            EstudianteCarreraDTOReq request) {

        Estudiante estudiante = estudianteRepository
                .findById(request.getIdEstudiante())
                .orElseThrow(() ->
                        new NotFoundException(
                                "Estudiante",
                                request.getIdEstudiante()));

        Carrera carrera = carreraRepository
                .findById(request.getIdCarrera())
                .orElseThrow(() ->
                        new NotFoundException(
                                "Carrera",
                                request.getIdCarrera()));

        EstudianteCarreraPK id = new EstudianteCarreraPK(
                estudiante.getDni(),
                carrera.getIdCarrera());

        EstudianteCarrera inscripcion = new EstudianteCarrera(
                id,
                estudiante,
                carrera,
                request.getInscripcion(),
                request.getGraduacion());

        estudianteCarreraRepository.guardar(inscripcion);

        return new EstudianteCarreraDTORes(inscripcion);
    }

    @Transactional(readOnly = true)
    public List<EstudianteCarreraDTORes> findAll() {
        return estudianteCarreraRepository.buscarTodos()
                .stream()
                .map(EstudianteCarreraDTORes::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public EstudianteCarreraDTORes findById(
            EstudianteCarreraPK id) {

        EstudianteCarrera inscripcion =
                estudianteCarreraRepository.buscarPorId(id);

        if (inscripcion == null) {
            throw new NotFoundException("EstudianteCarrera", id);
        }

        return new EstudianteCarreraDTORes(inscripcion);
    }
}