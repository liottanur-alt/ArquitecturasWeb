package Service;

import Entities.Carrera;
import Repository.MySql.MySQLEstudianteCarreraRepository;
import Repository.MySql.MySqlCarreraRepository;
import Service.DTO.Carrera.Req.CarrerasDTOReq;
import Service.DTO.Carrera.Res.CarreraDTO;
import Service.DTO.Carrera.Res.CarrerasDTORes;
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
import java.util.*;

@Service
@RequiredArgsConstructor
public class CarreraService {


    private final MySqlCarreraRepository carreraRepository ;
    private final MySQLEstudianteCarreraRepository estudianteCarreraRepository;

    @Transactional
    public CarrerasDTORes save(CarrerasDTOReq request) {
        final var carrera = new Carrera();
        carrera.setNombreCarrera(request.getNombreCarrera());
        carrera.setDuracion(request.getDuracion());

        final var result = this.carreraRepository.save(carrera);
        return new CarrerasDTORes(result);
    }

    @Transactional(readOnly = true)
    public List<CarrerasDTORes> findAll() {
        return this.carreraRepository.findAll().stream()
                .map(CarrerasDTORes::new)
                .toList();
    }

   @Transactional(readOnly = true)
    public CarrerasDTORes findById(Integer id) {
        return this.carreraRepository.findById(id).map(CarrerasDTORes::new).orElseThrow(() ->new NotFoundException("Carrera", id));
    }

    @Transactional(readOnly = true)
    public List<CarreraDTO> findCarrerasOrdenadasInscriptos(){
        return this.carreraRepository.findCarrerasOrdenadasInscriptos().stream()
                .map(CarreraDTO::new)
                .toList();
    }

    @Transactional
    public void cargarCarrerasDesdeCsv() {
        InputStream stream = getClass().getResourceAsStream("/carreras.csv");

        if (stream == null) {
            throw new IllegalStateException(
                    "No se encontró el recurso carreras.csv en src/main/resources"
            );
        }

        cargarCarrerasDesdeCsv(new InputStreamReader(stream, StandardCharsets.UTF_8)
        );
    }
    @Transactional
    public void cargarCarrerasDesdeCsv(Reader reader) {
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(reader)) {
            List<Carrera> carreras = new ArrayList<>();
            for (CSVRecord row : parser) {
                int idCarrera = Integer.parseInt(row.get("id_carrera"));
                String nombreCarrera = row.get("carrera");
                int duracion = Integer.parseInt(row.get("duracion"));
                Carrera carrera = new Carrera(idCarrera, nombreCarrera,duracion);
                carreras.add(carrera);
            }
            carreraRepository.saveAll(carreras);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Error al procesar el archivo CSV de carreras", e
            );
        }
    }

    // 3) Generar un reporte de las carreras (inscriptos y egresados por año).
    // Se ordenan alfabéticamente por carrera y cronológicamente por año.
    @Transactional(readOnly = true)
    public List<CarreraDTO> generarReporte() {

        Map<String, CarreraDTO> reporteMap = new HashMap<>();

        // 1. Obtener inscriptos por carrera y año
        List<Object[]> resultadosInscriptos = estudianteCarreraRepository.contarInscriptosPorCarreraYAnio();

        for (Object[] fila : resultadosInscriptos) {
            String carrera = (String) fila[0];
            int anio = ((Number) fila[1]).intValue();
            long cantidad = ((Number) fila[2]).longValue();
            String clave = carrera + "_" + anio;
            CarreraDTO dto = reporteMap.computeIfAbsent(clave, k -> new CarreraDTO(carrera, anio));
            dto.setInscriptos(cantidad);
        }
        // 2. Obtener egresados por carrera y año
        List<Object[]> resultadosEgresados = estudianteCarreraRepository.contarEgresadosPorCarreraYAnio();

        for (Object[] fila : resultadosEgresados) {
            String carrera = (String) fila[0];
            int anio = ((Number) fila[1]).intValue();
            long cantidad = ((Number) fila[2]).longValue();
            String clave = carrera + "_" + anio;
            CarreraDTO dto = reporteMap.computeIfAbsent(clave, k -> new CarreraDTO(carrera, anio));
            dto.setEgresados(cantidad);
        }
        // 3. Convertir a lista y ordenar
        List<CarreraDTO> listaReporte =new ArrayList<>(reporteMap.values());
        listaReporte.sort(Comparator.comparing(CarreraDTO::getNombreCarrera).thenComparing(CarreraDTO::getAnio));

        return listaReporte;
    }
}
