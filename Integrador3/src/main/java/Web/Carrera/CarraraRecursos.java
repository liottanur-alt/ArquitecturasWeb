package Web.Carrera;

import Entities.Carrera;
import Servicios.CarreraServicios;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/carreras")
@RequiredArgsConstructor
public class CarraraRecursos {

    private final CarreraServicios carreraServicios

    @GetMapping
    public ResponseEntity<List<Carrera>> findAll() {
        List<Carrera> carreras = this.carreraServicios.findAll();
        return ResponseEntity.ok(carreras);
    }

    @GetMapping
    public ResponseEntity<List<Carrera>>
}
