package Web.Carrera;

import Service.CarreraService;
import Service.DTO.Carrera.Res.CarreraDTO;
import Service.DTO.Carrera.Req.CarrerasDTOReq;
import Service.DTO.Carrera.Res.CarreraDTO;
import Service.DTO.Carrera.Res.CarrerasDTORes;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/carreras")
@RequiredArgsConstructor
public class CarreraRecursos {

    private final CarreraService carreraService;

    @GetMapping
    public List<CarrerasDTORes> findAll() {
        return this.carreraService.findAll();
    }

    @GetMapping("/{id}")
    public CarrerasDTORes findById(@PathVariable Integer id) {
        return this.carreraService.findById(id);
    }

    @PostMapping
    public ResponseEntity<CarrerasDTORes> save(@RequestBody @Valid CarrerasDTOReq request) {
        final var result = this.carreraService.save(request);
        return ResponseEntity.ok().body(result);
    }

    @GetMapping("/con-inscriptos")
    public List<CarreraDTO> findCarrerasConInscriptos() {
        return this.carreraService.findCarrerasOrdenadasInscriptos();
    }
    @PostMapping("/cargar-csv")
    public ResponseEntity<String> cargarDesdeCsv() {
        carreraService.cargarCarrerasDesdeCsv();
        return ResponseEntity.ok("Archivo CSV procesado e insertado en la base de datos correctamente.");
    }
    @GetMapping("/reporte")
    public ResponseEntity<List<CarreraDTO>> generarReporte() {
        return ResponseEntity.ok(carreraService.generarReporte());
    }
}
