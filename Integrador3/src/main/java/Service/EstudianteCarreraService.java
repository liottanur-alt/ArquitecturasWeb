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
        InputStream stream =getClass().getResourceAsStream("/estudianteCarrera.csv");

        if (stream == null) {
            throw new IllegalStateException("No se encontró el recurso estudiantecarrera.csv en src/main/resources");
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
                long dniEstudiante = Long.parseLong(row.get("id_estudiante"));
                int idCarrera = Integer.parseInt(row.get("id_carrera"));
                int anioInscripcion = Integer.parseInt(row.get("inscripcion"));
                int anioGraduacion = Integer.parseInt(row.get("graduacion"));

                Estudiante estudiante = estudianteRepository
                        .findById(dniEstudiante).orElse(null);

                Carrera carrera = carreraRepository
                        .findById(idCarrera).orElse(null);

                if (estudiante == null) {
                    System.err.println(
                            "ADVERTENCIA: El estudiante DNI "
                                    + dniEstudiante + " no existe en la BD."
                    );
                    continue;
                }

                if (carrera == null) {
                    System.err.println(
                            "ADVERTENCIA: La carrera ID "
                                    + idCarrera + " no existe en la BD."
                    );
                    continue;
                }

                EstudianteCarrera relacion = new EstudianteCarrera(
                        new EstudianteCarreraPK(dniEstudiante, idCarrera),
                        estudiante,
                        carrera,
                        LocalDate.of(anioInscripcion, 1, 1),
                        anioGraduacion == 0
                                ? null
                                : LocalDate.of(anioGraduacion, 1, 1)
                );

                relaciones.add(relacion);
            }

            estudianteCarreraRepository.saveAll(relaciones);

            System.out.println(
                    "Datos de EstudianteCarrera cargados con éxito!"
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Error al procesar el archivo CSV de estudiante-carrera",
                    e
            );
        }
    }
}