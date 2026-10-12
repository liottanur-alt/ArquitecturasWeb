package Servicios;

import Entities.Carrera;
import Repository.MySql.MySqlCarreraRepository;
import Servicios.DTO.Carrera.Req.CarrerasDTOReq;
import Servicios.DTO.Carrera.Res.CarrerasDTORes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CarreraServicios {


    private final MySqlCarreraRepository carreraRepository ;

    @Transactional
    public CarrerasDTORes save(CarrerasDTOReq request) {
        final var carrera = new Carrera();
        carrera.setNombreCarrera(request.getNombreCarrera());
        carrera.setDuracion(request.getDuracion());

        final var result = this.carreraRepository.save(carrera);
        return new CarrerasDTORes(result);
    }

    @Transactional(readOnly = true)
    public List<CarrerasDTORes> findAll() {
        return this.carreraRepository.findAll().stream()
                .map(CarrerasDTORes::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public CarrerasDTORes findById(Integer id) {
        return this.carreraRepository.findById(id)
                .map(CarrerasDTORes::new)
                .orElseThrow(() -> new RuntimeException("Carrera no encontrada con id: " + id));
    }

    @Transactional(readOnly = true)
    public List<CarrerasDTORes> findCarrerasOrdenadasInscriptos(){
        return this.carreraRepository.findCarrerasOrdenadasInscriptos().stream()
                .map(CarrerasDTORes::new)
                .toList();
    }
}
