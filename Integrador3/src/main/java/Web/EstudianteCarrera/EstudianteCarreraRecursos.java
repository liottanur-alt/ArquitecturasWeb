package Web.EstudianteCarrera;

import Entities.EstudianteCarreraPK;
import Servicios.EstudianteCarreraServicios;
import Servicios.DTO.EstudianteCarrera.Req.EstudianteCarreraDTOReq;
import Servicios.DTO.EstudianteCarrera.Res.EstudianteCarreraDTORes;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/estudiantes-carreras")
@RequiredArgsConstructor
public class EstudianteCarreraRecursos {

    private final EstudianteCarreraServicios estudianteCarreraServicios;

    @GetMapping
    public ResponseEntity<List<EstudianteCarreraDTORes>> findAll() {
        return ResponseEntity.ok(
                estudianteCarreraServicios.findAll()
        );
    }

    @GetMapping("/{idEstudiante}/{idCarrera}")
    public ResponseEntity<EstudianteCarreraDTORes> findById(
            @PathVariable Long idEstudiante,
            @PathVariable Integer idCarrera) {

        EstudianteCarreraPK id =
                new EstudianteCarreraPK(idEstudiante, idCarrera);

        return ResponseEntity.ok(
                estudianteCarreraServicios.findById(id)
        );
    }

    @PostMapping
    public ResponseEntity<EstudianteCarreraDTORes> matricular(
            @RequestBody EstudianteCarreraDTOReq request) {

        return ResponseEntity.ok(
                estudianteCarreraServicios.matricular(request)
        );
    }
}