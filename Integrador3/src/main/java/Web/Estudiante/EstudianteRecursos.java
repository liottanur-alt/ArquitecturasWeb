package Web.Estudiante;

import Service.EstudianteService;
import Service.DTO.Estudiante.Req.EstudianteDTOReq;
import Service.DTO.Estudiante.Res.EstudianteDTORes;
import Service.DTO.EstudianteDTOCarreraYCiudad;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/estudiantes")
public class EstudianteRecursos {

    private final EstudianteService estudianteService;

    public EstudianteRecursos(EstudianteService estudianteService) {
        this.estudianteService = estudianteService;
    }

    // -------------------------------------------------------------------
    // CARGA MASIVA
    // -------------------------------------------------------------------

    // Cargar datos masivos desde el archivo estudiantes.csv de resources
    @PostMapping("/cargar-csv")
    public ResponseEntity<String> cargarDesdeCsv() {
        estudianteService.cargarDesdeCsv();
        return ResponseEntity.ok("Archivo CSV procesado e insertado en la base de datos correctamente.");
    }

    // -------------------------------------------------------------------
    // OPERACIONES REST
    // -------------------------------------------------------------------

    // a) Dar de alta un estudiante
    @PostMapping
    public ResponseEntity<EstudianteDTORes> guardar(@RequestBody EstudianteDTOReq dtoReq) {
        EstudianteDTORes creado = estudianteService.guardar(dtoReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    // c) Recuperar todos los estudiantes ordenados alfabéticamente
    @GetMapping
    public ResponseEntity<List<EstudianteDTORes>> buscarTodos() {
        List<EstudianteDTORes> estudiantes = estudianteService.buscarTodosDTO();
        return ResponseEntity.ok(estudiantes);
    }

    // d) Recuperar un estudiante por su Número de Libreta Universitaria (LU)
    @GetMapping("/lu/{lu}")
    public ResponseEntity<EstudianteDTORes> buscarPorLU(@PathVariable int lu) {
        return estudianteService.buscarPorLUDTO(lu)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // e) Recuperar todos los estudiantes en base a su género
    @GetMapping("/genero/{genero}")
    public ResponseEntity<List<EstudianteDTORes>> buscarPorGenero(@PathVariable String genero) {
        List<EstudianteDTORes> estudiantes = estudianteService.buscarPorGeneroDTO(genero);
        return ResponseEntity.ok(estudiantes);
    }

    // g) Recuperar los estudiantes de una determinada carrera, filtrados por ciudad de residencia
    @GetMapping("/carrera/{idCarrera}/ciudad/{ciudad}")
    public ResponseEntity<List<EstudianteDTOCarreraYCiudad>> buscarPorCarreraYCiudad(
            @PathVariable int idCarrera,
            @PathVariable String ciudad) {
        List<EstudianteDTOCarreraYCiudad> estudiantes = estudianteService.buscarPorCarreraYCiudadDTO(idCarrera, ciudad);
        return ResponseEntity.ok(estudiantes);
    }

    // Eliminar un estudiante por su DNI
    @DeleteMapping("/{dni}")
    public ResponseEntity<Void> eliminarPorDni(@PathVariable Long dni) {
        estudianteService.eliminarPorDni(dni);
        return ResponseEntity.noContent().build();
    }
}