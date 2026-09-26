package Repository.MySql;
import Factory.JPAUtil;
import Repository.RepoInterfaz;
import Entities.EstudianteCarrera;
import Entities.Estudiante;
import Entities.Carrera;
import jakarta.persistence.EntityManager;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.util.ArrayList;

public class MySQLEstudianteCarreraRepository implements RepoInterfaz<EstudianteCarrera> {
    @Override
    public void guardar(EstudianteCarrera objeto) { //guarda una nueva inscripcion
        EntityManager em = JPAUtil.getEntityManager();
        em.getTransaction().begin();
        em.persist(objeto);
        em.getTransaction().commit();
        em.close();
    }

    @Override
    public EstudianteCarrera buscarPorId(int id) { // busca una inscripcion por su id
        EntityManager em = JPAUtil.getEntityManager();
        EstudianteCarrera estudianteCarrera =em.find(EstudianteCarrera.class, id);
        em.close();
        return estudianteCarrera;
    }

    @Override
    public List<EstudianteCarrera> buscarTodos() { // trae todas las inscripciones
        EntityManager em = JPAUtil.getEntityManager();
        List<EstudianteCarrera> lista = em.createQuery(
                "SELECT ec FROM EstudianteCarrera ec",
                EstudianteCarrera.class
        ).getResultList();
        em.close();
        return lista;
    }

    @Override
    public void eliminar(EstudianteCarrera objeto) {
        EntityManager em = JPAUtil.getEntityManager();
        em.getTransaction().begin();
        EstudianteCarrera entidad = em.merge(objeto);
        em.remove(entidad);
        em.getTransaction().commit();
        em.close();
    }
    //b) matricular un estudiante en una carrera
    public void matricular(Estudiante estudiante, Carrera carrera,
                           int inscripcion, int antiguedad) {
        EstudianteCarrera estudianteCarrera = new EstudianteCarrera(
                0,
                estudiante,
                carrera,
                inscripcion,
                0,
                antiguedad
        );
        guardar(estudianteCarrera);
    }
    public void insertarDatosCsv() {
        try {
            ArrayList<EstudianteCarrera> relaciones = new ArrayList<>();

            CSVParser parser = CSVFormat.DEFAULT
                    .withHeader()
                    .parse(new FileReader("src/main/resources/estudianteCarrera.csv"));

            EntityManager em = JPAUtil.getEntityManager();

            for (CSVRecord row : parser) {

                int id = Integer.parseInt(row.get("id"));
                int dniEstudiante = Integer.parseInt(row.get("id_estudiante"));
                int idCarrera = Integer.parseInt(row.get("id_carrera"));
                int inscripcion = Integer.parseInt(row.get("inscripcion"));
                int graduacion = Integer.parseInt(row.get("graduacion"));
                int antiguedad = Integer.parseInt(row.get("antiguedad"));

                Estudiante estudiante =
                        em.find(Estudiante.class, dniEstudiante);

                Carrera carrera =
                        em.find(Carrera.class, idCarrera);

                relaciones.add(new EstudianteCarrera(
                        id,
                        estudiante,
                        carrera,
                        inscripcion,
                        graduacion,
                        antiguedad
                ));
            }

            em.close();

            this.insertarDatos(relaciones);

        } catch (Exception e) {
            System.out.println(e);
        }
    }
    public void insertarDatos(ArrayList<EstudianteCarrera> relaciones) {
        EntityManager em = JPAUtil.getEntityManager();

        try {
            em.getTransaction().begin();

            for (EstudianteCarrera ec : relaciones) {
                em.persist(ec);
            }

            em.getTransaction().commit();

            System.out.println("Datos de EstudianteCarrera cargados con éxito!");

        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;

        } finally {
            em.close();
        }
    }
}
