package Repository;

import Entities.EstudianteCarrera;
import Entities.EstudianteCarreraPK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EstudianteCarreraRepository
            extends JpaRepository<EstudianteCarrera, EstudianteCarreraPK> {
}
