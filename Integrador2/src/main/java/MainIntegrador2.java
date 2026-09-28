package main.java;

import main.java.DTO.CarreraDTO;
import main.java.DTO.EstudianteDTO;
import main.java.Entities.Carrera;
import main.java.Entities.Estudiante;
import main.java.Entities.EstudianteCarrera;
import main.java.Entities.EstudianteCarreraPK;
import main.java.Repository.MySql.MySQLEstudianteCarreraRepository;
import main.java.Repository.MySql.MySqlCarreraRepository;
import main.java.Repository.MySql.MySqlEstudianteRepository;

import java.time.LocalDate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public class MainIntegrador2 {

    public static void main(String[] args) {
        Scanner entrada = new Scanner(System.in);
        MySqlEstudianteRepository estudiantes = new MySqlEstudianteRepository();
        MySqlCarreraRepository carreras = new MySqlCarreraRepository();
        MySQLEstudianteCarreraRepository matriculas = new MySQLEstudianteCarreraRepository();

        try {
            cargarCsvSiTablasVacias(estudiantes, carreras, matriculas);
        } catch (Exception e) {
            System.out.println("No se pudieron cargar los CSV: " + e.getMessage());
        }

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

    private static void cargarCsvSiTablasVacias(MySqlEstudianteRepository estudiantes,
                                                 MySqlCarreraRepository carreras,
                                                 MySQLEstudianteCarreraRepository matriculas) throws IOException {
        if (estudiantes.buscarTodos().isEmpty()) {
            estudiantes.cargarDesdeCsv(rutaCsv("estudiantes.csv"));
        }
        if (carreras.buscarTodos().isEmpty()) {
            carreras.insertarDatosCsv(rutaCsv("carreras.csv"));
        }
        if (matriculas.buscarTodos().isEmpty()) {
            matriculas.insertarDatosCsv(rutaCsv("estudianteCarrera.csv"));
        }
    }

    private static String rutaCsv(String nombre) throws IOException {
        Path[] rutas = {
                Path.of("Integrador2", "src", "main", "Resources", nombre),
                Path.of("src", "main", "Resources", nombre),
                Path.of("Integrador2", "src", "main", "resources", nombre),
                Path.of("src", "main", "resources", nombre)
        };
        for (Path ruta : rutas) {
            if (Files.isRegularFile(ruta)) {
                return ruta.toString();
            }
        }
        throw new IOException("No se encontró " + nombre + " en Resources.");
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
