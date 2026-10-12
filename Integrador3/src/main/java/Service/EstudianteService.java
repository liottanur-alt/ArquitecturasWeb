package Service;

import Entities.Estudiante;
import Repository.MySql.MySqlEstudianteRepository;
import Service.DTO.Estudiante.Req.EstudianteDTOReq;
import Service.DTO.Estudiante.Res.EstudianteDTORes;
import Service.DTO.EstudianteDTOCarreraYCiudad;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class EstudianteService {

    private final MySqlEstudianteRepository estudianteRepository;

    // Inyección de dependencias por constructor
    public EstudianteService(MySqlEstudianteRepository estudianteRepository) {
        this.estudianteRepository = estudianteRepository;
    }

    // ==========================================
    // 1. CARGA MASIVA DESDE CSV
    // ==========================================

    @Transactional
    public void cargarDesdeCsv() {
        InputStream stream = getClass().getResourceAsStream("classpath:estudiantes.csv");
        if (stream == null) {
            throw new IllegalStateException("No se encontró el recurso estudiantes.csv en src/main/resources");
        }
        cargarDesdeCsv(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    @Transactional
    public void cargarDesdeCsv(String rutaArchivo) {
        try {
            cargarDesdeCsv(Files.newBufferedReader(Paths.get(rutaArchivo), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Error al cargar estudiantes desde la ruta: " + rutaArchivo, e);
        }
    }

    @Transactional
    public void cargarDesdeCsv(Reader reader) {
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
            List<Estudiante> estudiantes = new ArrayList<>();

            for (CSVRecord row : parser) {
                Long dni = Long.parseLong(row.get("DNI"));
                String nombre = row.get("nombre");
                String apellido = row.get("apellido");
                int edadCsv = Integer.parseInt(row.get("edad"));
                String genero = row.get("genero");
                String ciudad = row.get("ciudad");
                int lu = Integer.parseInt(row.get("LU"));

                Estudiante estudiante = new Estudiante(dni, nombre, apellido, edadCsv, genero, ciudad, lu);
                estudiantes.add(estudiante);
            }

            // Inserción en lote optimizada mediante Spring Data JPA
            estudianteRepository.saveAll(estudiantes);
            System.out.println("Estudiantes procesados e insertados desde el CSV correctamente.");

        } catch (Exception e) {
            throw new IllegalStateException("Error al procesar el archivo CSV de estudiantes", e);
        }
    }

    // ==========================================
    // 2. OPERACIONES MAPPINGS (REQ / RES)
    // ==========================================

    // a) Guardar/Alta de un estudiante a partir del DTOReq
    @Transactional
    public EstudianteDTORes guardar(EstudianteDTOReq dtoReq) {
        // Mapeo Request -> Entidad
        Estudiante estudiante = new Estudiante(
                dtoReq.getDni(),
                dtoReq.getNombre(),
                dtoReq.getApellido(),
                dtoReq.getEdad(),
                dtoReq.getGenero(),
                dtoReq.getCiudad(),
                dtoReq.getLu()
        );

        Estudiante guardado = estudianteRepository.save(estudiante);

        // Mapeo Entidad -> Response
        return mapToDTORes(guardado);
    }

    // b) Buscar por DNI (Devuelve DTO de respuesta)
    @Transactional(readOnly = true)
    public Optional<EstudianteDTORes> buscarPorDni(Long dni) {
        return estudianteRepository.findById(dni)
                .map(this::mapToDTORes);
    }

    // c) Recuperar todos los estudiantes ordenados alfabéticamente (DTO)
    @Transactional(readOnly = true)
    public List<EstudianteDTORes> buscarTodosDTO() {
        return estudianteRepository.buscarTodosDTO();
    }

    // d) Recuperar por Libreta Universitaria (LU)
    @Transactional(readOnly = true)
    public Optional<EstudianteDTORes> buscarPorLUDTO(int lu) {
        return estudianteRepository.buscarPorLUDTO(lu);
    }

    // e) Filtrar por género
    @Transactional(readOnly = true)
    public List<EstudianteDTORes> buscarPorGeneroDTO(String genero) {
        return estudianteRepository.buscarPorGeneroDTO(genero);
    }

    // g) Filtrar por carrera y ciudad
    @Transactional(readOnly = true)
    public List<EstudianteDTOCarreraYCiudad> buscarPorCarreraYCiudadDTO(int idCarrera, String ciudad) {
        return estudianteRepository.buscarPorCarreraYCiudadDTO(idCarrera, ciudad);
    }

    // f) Eliminar un estudiante por su DNI
    @Transactional
    public void eliminarPorDni(Long dni) {
        estudianteRepository.deleteById(dni);
    }

    // ==========================================
    // METODO AUXILIAR DE MAPEO
    // ==========================================
    private EstudianteDTORes mapToDTORes(Estudiante e) {
        return new EstudianteDTORes(
                e.getDni(),
                e.getNombre(),
                e.getApellido(),
                e.getGenero(),
                e.getCiudad(),
                e.getLu(),
                e.getFechaNacimiento()
        );
    }
}