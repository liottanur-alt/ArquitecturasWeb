package Repository.MySql;

import Entities.Carrera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MySqlCarreraRepository extends JpaRepository<Carrera, Integer>{

    @Query("SELECT c FROM Carrera c JOIN c.EstudianteCarrera ec GROUP BY c ORDER BY COUNT(ec) DESC")
    List<Carrera> findCarrerasOrdenadasInscriptos();
}
