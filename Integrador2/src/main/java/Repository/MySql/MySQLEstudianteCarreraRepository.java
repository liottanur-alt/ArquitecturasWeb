package Repository.MySql;
import Factory.JPAUtil;
import Repository.RepoInterfaz;
import Entities.EstudianteCarrera;
import entities.Estudiante;
import jakarta.persistence.EntityManager;
import java.util.List;

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
    //f) recuperar las carreras con estudiantes inscriptos, y ordenar por cantidad de inscriptos
    public List<Object[]> carrerasConCantidadDeInscriptos() {
        EntityManager em = JPAUtil.getEntityManager();
        List<Object[]> resultado = em.createQuery(
                "SELECT ec.carrera, COUNT(ec.estudiante) " +
                        "FROM EstudianteCarrera ec " +
                        "GROUP BY ec.carrera " +
                        "ORDER BY COUNT(ec.estudiante) DESC",
                Object[].class // uso objeto porque la consulta devuelve dos cosas, la carrera y la cantidad de inscriptos
        ).getResultList();
        em.close();
        return resultado;
    }
    //g) recuperar los estudiantes de una determinada carrera, filtrado por ciudad de residencia
    public List<Estudiante> estudiantesPorCarreraYCiudad(int idCarrera, String ciudad) {
        EntityManager em = JPAUtil.getEntityManager();
        List<Estudiante> resultado = em.createQuery(
                        "SELECT ec.estudiante " +
                                "FROM EstudianteCarrera ec " +
                                "WHERE ec.carrera.idCarrera = :idCarrera " +
                                "AND ec.estudiante.ciudad = :ciudad",
                        Estudiante.class
                )
                .setParameter("idCarrera", idCarrera)
                .setParameter("ciudad", ciudad)
                .getResultList();
        em.close();
        return resultado;
    }

}
