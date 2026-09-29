import DTO.CarreraDTO;
import DTO.EstudianteCarreraDTO;
import DTO.EstudianteDTO;
import DTO.EstudianteDTOCarreraYCiudad;
import Entities.Carrera;
import Entities.Estudiante;
import Entities.EstudianteCarrera;
import Entities.EstudianteCarreraPK;
import Repository.MySql.MySQLEstudianteCarreraRepository;
import Repository.MySql.MySqlCarreraRepository;
import Repository.MySql.MySqlEstudianteRepository;
import jakarta.persistence.NoResultException;

import java.time.LocalDate;
import java.util.List;

public class ProbarMetodosMySql {

    public static void main(String[] args) {
        MySQLEstudianteCarreraRepository estudianteCarreraRepository =
                new MySQLEstudianteCarreraRepository();
        MySqlCarreraRepository carreraRepository = new MySqlCarreraRepository();
        MySqlEstudianteRepository estudianteRepository = new MySqlEstudianteRepository();

        Estudiante estudianteDePrueba = null;
        Carrera carreraDePrueba = null;

        try {
            System.out.println("=== MySqlCarreraRepository ===");
            List<Carrera> carreras = carreraRepository.buscarTodos();
            requireData(carreras, "No hay carreras en la base de datos.");
            System.out.println("buscarTodos(): " + carreras);

            Carrera carreraEncontrada = carreraRepository.buscarPorId(carreras.get(0).getIdCarrera());
            System.out.println("buscarPorId(): " + carreraEncontrada);

            System.out.println("getCarrerasConInscriptos(): "
                    + carreraRepository.getCarrerasConInscriptos());

            List<CarreraDTO> reporte = carreraRepository.generarReporte();
            System.out.println("generarReporte() - datos consultados de MySQL: "
                    + reporte.size() + " filas");
            for (CarreraDTO fila : reporte) {
                System.out.printf("  %s | %d | inscriptos: %d | egresados: %d%n",
                        fila.getNombreCarrera(), fila.getAnio(),
                        fila.getInscriptos(), fila.getEgresados());
            }

            System.out.println("\n=== MySqlEstudianteRepository ===");
            List<EstudianteDTO> estudiantes = estudianteRepository.buscarTodosDTO();
            requireData(estudiantes, "No hay estudiantes en la base de datos.");
            EstudianteDTO estudianteExistente = estudiantes.get(0);
            System.out.println("buscarTodosDTO(): " + estudiantes.size() + " estudiantes");
            estudiantes.stream().limit(5).forEach(estudiante -> System.out.println("  " + estudiante));

            Estudiante nuevoEstudiante = crearEstudianteDePrueba(estudianteRepository);
            estudianteRepository.guardar(nuevoEstudiante);
            estudianteDePrueba = nuevoEstudiante;
            System.out.println("guardar(Estudiante): estudiante de prueba guardado.");

            System.out.println("buscarPorId(long dni): "
                    + estudianteRepository.buscarPorId(estudianteDePrueba.getDni()).getDni());
            System.out.println("buscarPorLUDTO(int lu): "
                    + estudianteRepository.buscarPorLUDTO(estudianteDePrueba.getLu()));

            List<EstudianteDTO> estudiantesPorGenero =
                    estudianteRepository.buscarPorGeneroDTO(estudianteDePrueba.getGenero());
            System.out.println("buscarPorGeneroDTO(String genero): "
                    + estudiantesPorGenero.size() + " resultados");

            System.out.println("\n=== MySQLEstudianteCarreraRepository ===");
            carreraDePrueba = carreraEncontrada;
            EstudianteCarreraDTO matricula = estudianteCarreraRepository.matricular(
                    estudianteDePrueba, carreraDePrueba, LocalDate.now());
            System.out.printf("matricular(Estudiante, Carrera): DNI %d en carrera %d, fecha %s%n",
                    matricula.getDniEstudiante(), matricula.getIdCarrera(),
                    matricula.getInscripcion());

            List<EstudianteDTOCarreraYCiudad> porCarreraYCiudad =
                    estudianteRepository.buscarPorCarreraYCiudadDTO(
                            carreraDePrueba.getIdCarrera(), estudianteDePrueba.getCiudad());
            System.out.println("buscarPorCarreraYCiudadDTO(int idCarrera, String ciudad): "
                    + porCarreraYCiudad.size() + " resultados");
            porCarreraYCiudad.stream().limit(5).forEach(estudiante ->
                    System.out.printf("  %s %s | DNI %d | %s%n",
                            estudiante.getNombre(), estudiante.getApellido(),
                            estudiante.getDni(), estudiante.getCiudad()));
        } finally {
            if (estudianteDePrueba != null) {
                if (carreraDePrueba != null) {
                    EstudianteCarreraPK idMatricula = new EstudianteCarreraPK(
                            estudianteDePrueba.getDni(), carreraDePrueba.getIdCarrera());
                    EstudianteCarrera matricula = estudianteCarreraRepository.buscarPorId(idMatricula);
                    if (matricula != null) {
                        estudianteCarreraRepository.eliminar(matricula);
                    }
                }
                estudianteRepository.eliminar(estudianteDePrueba);
                System.out.println("\nSe eliminaron los datos temporales de prueba.");
            }
        }
    }

    private static Estudiante crearEstudianteDePrueba(MySqlEstudianteRepository repository) {
        long dniBase = System.currentTimeMillis() * 1_000L;
        int luBase = 100_000_000;

        for (int intento = 0; intento < 1000; intento++) {
            long dni = dniBase + intento;
            int lu = luBase + intento;
            if (repository.buscarPorId(dni) == null && libretaDisponible(repository, lu)) {
                return new Estudiante(dni, "Estudiante", "Prueba", 25,
                        "Prueba", "Ciudad de prueba", lu);
            }
        }
        throw new IllegalStateException("No se pudo generar un DNI y una LU disponibles para la prueba.");
    }

    private static boolean libretaDisponible(MySqlEstudianteRepository repository, int lu) {
        try {
            repository.buscarPorLUDTO(lu);
            return false;
        } catch (NoResultException e) {
            return true;
        }
    }

    private static void requireData(List<?> data, String message) {
        if (data.isEmpty()) {
            throw new IllegalStateException(message);
        }
    }
}
