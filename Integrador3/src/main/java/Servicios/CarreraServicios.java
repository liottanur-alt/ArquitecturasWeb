package Servicios;

import Entities.Carrera;
import Repository.MySql.MySqlCarreraRepository;
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
    public Carrera save(Carrera carrera) {
        return this.carreraRepository.save(carrera);
    }

    @Transactional(readOnly = true)
    public List<Carrera> findAll() {
        return this.carreraRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Carrera> findById(Integer Id) {
        return this.carreraRepository.findById(Id);
    }
}
