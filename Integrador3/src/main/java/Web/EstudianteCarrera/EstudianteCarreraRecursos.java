package Web.EstudianteCarrera;

import Entities.EstudianteCarreraPK;
import Service.EstudianteCarreraService;
import Service.DTO.EstudianteCarrera.Req.EstudianteCarreraDTOReq;
import Service.DTO.EstudianteCarrera.Res.EstudianteCarreraDTORes;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/estudiantes-carreras")
@RequiredArgsConstructor
public class EstudianteCarreraRecursos {

    private final EstudianteCarreraService estudianteCarreraService;

    @GetMapping
    public ResponseEntity<List<EstudianteCarreraDTORes>> findAll() {
        return ResponseEntity.ok(
                estudianteCarreraService.findAll()
        );
    }

    @GetMapping("/{idEstudiante}/{idCarrera}")
    public ResponseEntity<EstudianteCarreraDTORes> findById(
            @PathVariable Long idEstudiante,
            @PathVariable Integer idCarrera) {

        EstudianteCarreraPK id =
                new EstudianteCarreraPK(idEstudiante, idCarrera);

        return ResponseEntity.ok(
                estudianteCarreraService.findById(id)
        );
    }

    @PostMapping
    public ResponseEntity<EstudianteCarreraDTORes> matricular(
            @RequestBody EstudianteCarreraDTOReq request) {

        return ResponseEntity.ok(
                estudianteCarreraService.matricular(request)
        );
    }
    @PostMapping("/cargar-csv")
    public ResponseEntity<String> cargarDesdeCsv() {
        estudianteCarreraService.cargarEstudianteCarreraDesdeCsv();
        return ResponseEntity.ok("Archivo CSV procesado e insertado en la base de datos correctamente.");
    }
}