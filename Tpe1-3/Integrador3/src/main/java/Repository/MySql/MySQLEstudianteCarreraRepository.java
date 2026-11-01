package Repository.MySql;

import Entities.Estudiante;


import Entities.EstudianteCarrera;
import Entities.EstudianteCarreraPK;
import Service.DTO.Estudiante.Res.EstudianteDTORes;
import Service.DTO.EstudianteCarrera.Res.EstudianteCarreraDTORes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MySQLEstudianteCarreraRepository extends JpaRepository<EstudianteCarrera, EstudianteCarreraPK> {

    @Query("SELECT new Service.DTO.EstudianteCarrera.Res.EstudianteCarreraDTORes(" +
            "ec.id.idEstudiante, ec.id.idCarrera, ec.inscripcion, ec.graduacion) " +
            "FROM EstudianteCarrera ec")
    List<EstudianteCarreraDTORes> buscarTodosDTO();
    @Query("""
    SELECT c.nombreCarrera, YEAR(ec.inscripcion), COUNT(ec)
    FROM EstudianteCarrera ec
    JOIN ec.carrera c
    GROUP BY c.nombreCarrera, YEAR(ec.inscripcion)
""")
    List<Object[]> contarInscriptosPorCarreraYAnio();

    @Query("""
    SELECT c.nombreCarrera, YEAR(ec.graduacion), COUNT(ec)
    FROM EstudianteCarrera ec
    JOIN ec.carrera c
    WHERE ec.graduacion IS NOT NULL
    GROUP BY c.nombreCarrera, YEAR(ec.graduacion)
""")
    List<Object[]> contarEgresadosPorCarreraYAnio();
}
