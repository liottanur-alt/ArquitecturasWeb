import DTO.CarreraDTO;
import DTO.EstudianteDTO;
import Entities.Carrera;
import Entities.Estudiante;
import Entities.EstudianteCarrera;
import Entities.EstudianteCarreraPK;
import Repository.MySql.MySQLEstudianteCarreraRepository;
import Repository.MySql.MySqlCarreraRepository;
import Repository.MySql.MySqlEstudianteRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Scanner;

public class MainIntegrador2 {

    public static void main(String[] args) {
        Scanner entrada = new Scanner(System.in);
        MySqlEstudianteRepository estudiantes = new MySqlEstudianteRepository();
        MySqlCarreraRepository carreras = new MySqlCarreraRepository();
        MySQLEstudianteCarreraRepository matriculas = new MySQLEstudianteCarreraRepository();

        int opcion;
        do {
            System.out.println("\n--- Prueba del TP ---");
            System.out.println("1. Dar de alta un estudiante");
            System.out.println("2. Matricular estudiante en carrera");
            System.out.println("3. Listar estudiantes");
            System.out.println("4. Buscar estudiante por LU");
            System.out.println("5. Buscar estudiantes por género");
            System.out.println("6. Listar carreras por cantidad de inscriptos");
            System.out.println("7. Buscar estudiantes por carrera y ciudad");
            System.out.println("8. Mostrar reporte anual de carreras");
            System.out.println("9. Buscar matrícula por DNI e ID de carrera");
            System.out.println("0. Salir");
            opcion = leerEntero(entrada, "Opción: ");

            try {
                switch (opcion) {
                    case 1 -> altaEstudiante(entrada, estudiantes);
                    case 2 -> matricular(entrada, estudiantes, carreras, matriculas);
                    case 3 -> mostrarEstudiantes(estudiantes.buscarTodosDTO());
                    case 4 -> buscarPorLU(entrada, estudiantes);
                    case 5 -> buscarPorGenero(entrada, estudiantes);
                    case 6 -> carreras.getCarrerasConInscriptos().forEach(System.out::println);
                    case 7 -> buscarPorCarreraYCiudad(entrada, estudiantes);
                    case 8 -> mostrarReporte(carreras.generarReporte());
                    case 9 -> buscarMatricula(entrada, matriculas);
                    case 0 -> System.out.println("Fin de la prueba.");
                    default -> System.out.println("Opción inválida.");
                }
            } catch (Exception e) {
                System.out.println("No se pudo completar la operación: " + e.getMessage());
            }
        } while (opcion != 0);

        entrada.close();
    }

    private static void altaEstudiante(Scanner entrada, MySqlEstudianteRepository repo) {
        long dni = leerLong(entrada, "DNI: ");
        String nombre = leerTexto(entrada, "Nombre: ");
        String apellido = leerTexto(entrada, "Apellido: ");
        int edad = leerEntero(entrada, "Edad: ");
        String genero = leerTexto(entrada, "Género: ");
        String ciudad = leerTexto(entrada, "Ciudad: ");
        int lu = leerEntero(entrada, "Libreta universitaria: ");

        repo.guardar(new Estudiante(dni, nombre, apellido, edad, genero, ciudad, lu));
        System.out.println("Estudiante guardado.");
    }

    private static void matricular(Scanner entrada, MySqlEstudianteRepository repoEstudiantes,
                                   MySqlCarreraRepository repoCarreras,
                                   MySQLEstudianteCarreraRepository repoMatriculas) {
        long dni = leerLong(entrada, "DNI del estudiante: ");
        int idCarrera = leerEntero(entrada, "ID de la carrera: ");
        Estudiante estudiante = repoEstudiantes.buscarPorId(dni);
        Carrera carrera = repoCarreras.buscarPorId(idCarrera);

        if (estudiante == null || carrera == null) {
            System.out.println("No se encontró el estudiante o la carrera.");
            return;
        }

        repoMatriculas.matricular(estudiante, carrera, LocalDate.now());
        System.out.println("Estudiante matriculado.");
    }

    private static void buscarPorLU(Scanner entrada, MySqlEstudianteRepository repo) {
        int lu = leerEntero(entrada, "Libreta universitaria: ");
        System.out.println(repo.buscarPorLUDTO(lu));
    }

    private static void buscarPorGenero(Scanner entrada, MySqlEstudianteRepository repo) {
        String genero = leerTexto(entrada, "Género: ");
        mostrarEstudiantes(repo.buscarPorGeneroDTO(genero));
    }

    private static void buscarPorCarreraYCiudad(Scanner entrada, MySqlEstudianteRepository repo) {
        int idCarrera = leerEntero(entrada, "ID de la carrera: ");
        String ciudad = leerTexto(entrada, "Ciudad: ");
        mostrarEstudiantes(repo.buscarPorCarreraYCiudadDTO(idCarrera, ciudad));
    }

    private static void buscarMatricula(Scanner entrada, MySQLEstudianteCarreraRepository repo) {
        long dni = leerLong(entrada, "DNI del estudiante: ");
        int idCarrera = leerEntero(entrada, "ID de la carrera: ");
        EstudianteCarrera matricula = repo.buscarPorId(new EstudianteCarreraPK(dni, idCarrera));

        if (matricula == null) {
            System.out.println("No se encontró esa matrícula.");
            return;
        }
        System.out.println("Matrícula encontrada: " + matricula.getInscripcion()
                + " - Graduación: " + matricula.getGraduacion());
    }

    private static void mostrarEstudiantes(List<EstudianteDTO> lista) {
        if (lista.isEmpty()) {
            System.out.println("No se encontraron estudiantes.");
            return;
        }
        lista.forEach(System.out::println);
    }

    private static void mostrarReporte(List<CarreraDTO> reporte) {
        if (reporte.isEmpty()) {
            System.out.println("No hay datos para el reporte.");
            return;
        }
        for (CarreraDTO fila : reporte) {
            System.out.printf("%s | %d | inscriptos: %d | egresados: %d%n",
                    fila.getNombreCarrera(), fila.getAnio(),
                    fila.getInscriptos(), fila.getEgresados());
        }
    }

    private static String leerTexto(Scanner entrada, String mensaje) {
        System.out.print(mensaje);
        return entrada.nextLine();
    }

    private static int leerEntero(Scanner entrada, String mensaje) {
        return Integer.parseInt(leerTexto(entrada, mensaje));
    }

    private static long leerLong(Scanner entrada, String mensaje) {
        return Long.parseLong(leerTexto(entrada, mensaje));
    }
}
