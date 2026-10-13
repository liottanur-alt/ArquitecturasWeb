 package Service;

import Entities.Carrera;
import Entities.Estudiante;
import Entities.EstudianteCarrera;
import Entities.EstudianteCarreraPK;
import Repository.MySql.MySQLEstudianteCarreraRepository;
import Repository.MySql.MySqlCarreraRepository;
import Repository.MySql.MySqlEstudianteRepository;
import Service.DTO.EstudianteCarrera.Req.EstudianteCarreraDTOReq;
import Service.DTO.EstudianteCarrera.Res.EstudianteCarreraDTORes;
import Service.exception.NotFoundException;

import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EstudianteCarreraService {

    private final MySQLEstudianteCarreraRepository estudianteCarreraRepository;
    private final MySqlEstudianteRepository estudianteRepository;
    private final MySqlCarreraRepository carreraRepository;

    @Transactional
    public EstudianteCarreraDTORes matricular(EstudianteCarreraDTOReq request) {

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


        EstudianteCarrera inscripcionGuardada = estudianteCarreraRepository.save(inscripcion);

        return new EstudianteCarreraDTORes(inscripcionGuardada);
    }


    @Transactional(readOnly = true)
    public List<EstudianteCarreraDTORes> findAll() {
        return estudianteCarreraRepository.buscarTodosDTO()
                .stream()
                .map(EstudianteCarreraDTORes::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public EstudianteCarreraDTORes findById(EstudianteCarreraPK id) {
        return estudianteCarreraRepository.findById(id)
                .map(EstudianteCarreraDTORes::new)
                .orElseThrow(() -> new NotFoundException("EstudianteCarrera", id));
    }
    public void cargarEstudianteCarreraDesdeCsv() {
        InputStream stream =getClass().getResourceAsStream("/estudiante_carrera.csv");

        if (stream == null) {
            throw new IllegalStateException("No se encontró el recurso estudiante_carrera.csv en src/main/resources");
        }
        cargarEstudianteCarreraDesdeCsv(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
    @Transactional
    public void cargarEstudianteCarreraDesdeCsv(Reader reader) {
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(reader)) {

            List<EstudianteCarrera> relaciones = new ArrayList<>();

            for (CSVRecord row : parser) {
                Long idEstudiante = Long.parseLong(row.get("id_estudiante"));
                int idCarrera = Integer.parseInt(row.get("id_carrera"));
                LocalDate inscripcion = LocalDate.parse(row.get("inscripcion"));

                String graduacionTexto = row.get("graduacion");

                LocalDate graduacion = graduacionTexto == null || graduacionTexto.isBlank() ? null : LocalDate.parse(graduacionTexto);

                Entities.Estudiante estudiante = estudianteRepository.findById(idEstudiante).orElseThrow(() -> new IllegalArgumentException("No existe el estudiante con ID " + idEstudiante));
                Carrera carrera = carreraRepository.findById(idCarrera)
                        .orElseThrow(() -> new IllegalArgumentException("No existe la carrera con ID " + idCarrera));

                EstudianteCarrera relacion = new EstudianteCarrera();
                relacion.setEstudiante(estudiante);
                relacion.setCarrera(carrera);
                relacion.setInscripcion(inscripcion);
                relacion.setGraduacion(graduacion);
                relaciones.add(relacion);
            }

            estudianteCarreraRepository.saveAll(relaciones);

            System.out.println("Relaciones estudiante-carrera cargadas correctamente.");

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Error al procesar el archivo CSV de estudiante-carrera", e
            );
        }
    }
}