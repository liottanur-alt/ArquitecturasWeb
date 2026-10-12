package Web.Carrera;

import Servicios.CarreraServicios;
import Servicios.DTO.Carrera.Req.CarrerasDTOReq;
import Servicios.DTO.Carrera.Res.CarrerasDTORes;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/carreras")
@RequiredArgsConstructor
public class CarreraRecursos {

    private final CarreraServicios carreraServicios;

    @GetMapping
    public List<CarrerasDTORes> findAll() {
        return this.carreraServicios.findAll();
    }

    @GetMapping("/{id}")
    public CarrerasDTORes findById(@PathVariable Integer id) {
        return this.carreraServicios.findById(id);
    }

    @PostMapping
    public ResponseEntity<CarrerasDTORes> save(@RequestBody @Valid CarrerasDTOReq request) {
        final var result = this.carreraServicios.save(request);
        return ResponseEntity.ok().body(result);
    }

    @GetMapping("/con-inscriptos")
    public List<CarrerasDTORes> findCarrerasConInscriptos() {
        return this.carreraServicios.findCarrerasOrdenadasInscriptos();
    }
}
