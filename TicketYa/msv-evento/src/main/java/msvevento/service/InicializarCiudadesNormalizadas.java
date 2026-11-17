package msvevento.service;

import msvevento.entity.Lugar;
import msvevento.repository.LugarRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Component
public class InicializarCiudadesNormalizadas implements ApplicationRunner {
    private final LugarRepository lugares;
    public InicializarCiudadesNormalizadas(LugarRepository lugares) { this.lugares = lugares; }

    // Completa registros anteriores a HU10 una vez al arrancar, sin tocar eventos ni cambiar la ciudad visible.
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Lugar lugar : lugares.findAll()) {
            if (!Objects.equals(lugar.getCiudadNormalizada(), CiudadNormalizada.de(lugar.getCiudad()))) {
                lugar.normalizarCiudad();
            }
        }
    }
}
