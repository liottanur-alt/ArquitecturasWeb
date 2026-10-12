package Web.Estudiante;

import Servicios.EstudianteServicios;
import Servicios.DTO.Estudiante.Req.EstudianteDTOReq;
import Servicios.DTO.Estudiante.Res.EstudianteDTORes;
import Servicios.DTO.EstudianteDTOCarreraYCiudad;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/estudiantes")
public class EstudianteRecursos {

    private final EstudianteServicios estudianteServicios;

    public EstudianteRecursos(EstudianteServicios estudianteServicios) {
        this.estudianteServicios = estudianteServicios;
    }

    // -------------------------------------------------------------------
    // CARGA MASIVA
    // -------------------------------------------------------------------

    // Cargar datos masivos desde el archivo estudiantes.csv de resources
    @PostMapping("/cargar-csv")
    public ResponseEntity<String> cargarDesdeCsv() {
        estudianteServicios.cargarDesdeCsv();
        return ResponseEntity.ok("Archivo CSV procesado e insertado en la base de datos correctamente.");
    }

    // -------------------------------------------------------------------
    // OPERACIONES REST
    // -------------------------------------------------------------------

    // a) Dar de alta un estudiante
    @PostMapping
    public ResponseEntity<EstudianteDTORes> guardar(@RequestBody EstudianteDTOReq dtoReq) {
        EstudianteDTORes creado = estudianteServicios.guardar(dtoReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    // c) Recuperar todos los estudiantes ordenados alfabéticamente
    @GetMapping
    public ResponseEntity<List<EstudianteDTORes>> buscarTodos() {
        List<EstudianteDTORes> estudiantes = estudianteServicios.buscarTodosDTO();
        return ResponseEntity.ok(estudiantes);
    }

    // d) Recuperar un estudiante por su Número de Libreta Universitaria (LU)
    @GetMapping("/lu/{lu}")
    public ResponseEntity<EstudianteDTORes> buscarPorLU(@PathVariable int lu) {
        return estudianteServicios.buscarPorLUDTO(lu)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // e) Recuperar todos los estudiantes en base a su género
    @GetMapping("/genero/{genero}")
    public ResponseEntity<List<EstudianteDTORes>> buscarPorGenero(@PathVariable String genero) {
        List<EstudianteDTORes> estudiantes = estudianteServicios.buscarPorGeneroDTO(genero);
        return ResponseEntity.ok(estudiantes);
    }

    // g) Recuperar los estudiantes de una determinada carrera, filtrados por ciudad de residencia
    @GetMapping("/carrera/{idCarrera}/ciudad/{ciudad}")
    public ResponseEntity<List<EstudianteDTOCarreraYCiudad>> buscarPorCarreraYCiudad(
            @PathVariable int idCarrera,
            @PathVariable String ciudad) {
        List<EstudianteDTOCarreraYCiudad> estudiantes = estudianteServicios.buscarPorCarreraYCiudadDTO(idCarrera, ciudad);
        return ResponseEntity.ok(estudiantes);
    }

    // Eliminar un estudiante por su DNI
    @DeleteMapping("/{dni}")
    public ResponseEntity<Void> eliminarPorDni(@PathVariable Long dni) {
        estudianteServicios.eliminarPorDni(dni);
        return ResponseEntity.noContent().build();
    }
}