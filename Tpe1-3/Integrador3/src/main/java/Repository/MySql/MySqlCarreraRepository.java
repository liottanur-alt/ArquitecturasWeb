package Repository.MySql;

import Entities.Carrera;
import Service.DTO.Carrera.Res.CarreraDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface MySqlCarreraRepository extends JpaRepository<Carrera, Integer>{
    @Query("""
    SELECT new Service.DTO.Carrera.Res.CarreraDTO(
        c.nombreCarrera,
        YEAR(ec.inscripcion),
        COUNT(ec),
        SUM(CASE WHEN ec.graduacion IS NOT NULL THEN 1 ELSE 0 END)
    )
    FROM EstudianteCarrera ec
    JOIN ec.carrera c
    GROUP BY c.nombreCarrera, YEAR(ec.inscripcion)
    ORDER BY COUNT(ec) DESC
""")
    List<CarreraDTO> findCarrerasOrdenadasInscriptos();
}
