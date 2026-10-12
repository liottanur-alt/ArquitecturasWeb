package Repository.MySql;

import Entities.Estudiante;
import Servicios.DTO.Estudiante.Res.EstudianteDTORes;
import Servicios.DTO.EstudianteDTOCarreraYCiudad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MySqlEstudianteRepository extends JpaRepository<Estudiante, Long> {

    // c) Recuperar todos los estudiantes ordenados alfabéticamente por apellido
    List<Estudiante> findAllByOrderByApellidoAsc();

    // c) Versión que retorna DTO de Respuesta directo con JPQL
    @Query("SELECT new Servicios.DTO.Estudiante.Res.EstudianteDTORes(e.dni, e.nombre, e.apellido, e.genero, e.ciudad, e.lu, e.fechaNacimiento) " +
            "FROM Estudiante e ORDER BY e.apellido ASC")
    List<EstudianteDTORes> buscarTodosDTO();

    // d) Recuperar un estudiante en base a su libreta universitaria (LU)
    @Query("SELECT new Servicios.DTO.Estudiante.Res.EstudianteDTORes(e.dni, e.nombre, e.apellido, e.genero, e.ciudad, e.lu, e.fechaNacimiento) " +
            "FROM Estudiante e WHERE e.lu = :lu")
    Optional<EstudianteDTORes> buscarPorLUDTO(@Param("lu") int lu);

    // e) Recuperar todos los estudiantes por género
    @Query("SELECT new Servicios.DTO.Estudiante.Res.EstudianteDTORes(e.dni, e.nombre, e.apellido, e.genero, e.ciudad, e.lu, e.fechaNacimiento) " +
            "FROM Estudiante e WHERE e.genero = :genero")
    List<EstudianteDTORes> buscarPorGeneroDTO(@Param("genero") String genero);

    // g) Recuperar los estudiantes de una determinada carrera, filtrado por ciudad de residencia
    @Query("SELECT new Servicios.DTO.EstudianteDTOCarreraYCiudad(" +
            "ec.estudiante.dni, " +
            "ec.estudiante.nombre, " +
            "ec.estudiante.apellido, " +
            "ec.estudiante.genero, " +
            "ec.estudiante.ciudad, " +
            "ec.estudiante.lu, " +
            "ec.estudiante.fechaNacimiento) " +
            "FROM EstudianteCarrera ec " +
            "WHERE ec.carrera.idCarrera = :idCarrera " +
            "AND ec.estudiante.ciudad = :ciudad")
    List<EstudianteDTOCarreraYCiudad> buscarPorCarreraYCiudadDTO(@Param("idCarrera") int idCarrera, @Param("ciudad") String ciudad);
}