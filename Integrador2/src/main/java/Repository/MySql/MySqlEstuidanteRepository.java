package Repository.MySql;

import Entities.*;
import main.java.Repository.*;
import main.java.Repository.RepoInterfaz;

import java.io.FileReader;
import java.util.List;

public class MySqlEstudianteRepository<Estudiante> implements RepoInterfaz {

    public void insertarDesdeCSV() {
        EntityManager em = JPAUtil.getEntityManager();
        try (CSVReader reader = new CSVReader(new FileReader("src/main/resources/estudiantes.csv"))) {
            String[] linea;
            reader.readNext(); // salta cabecera

            em.getTransaction().begin();

            while ((linea = reader.readNext()) != null) {
                Estudiante estudiante = new Estudiante(Integer.parseInt(linea[0]), linea[1], linea[2], linea[4], Integer.parseInt(linea[3]), linea[5], Integer.parseInt(linea[6]), new ArrayList<>());
                em.persist(estudiante);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            System.err.println("Error al insertar las carreras");
            e.printStackTrace();
        } finally {
            em.close();
        }
    }
    public void insertarEstudiante(Estudiante es){
        EntityManager em = JPAUtil.getEntityManager();
        em.getTransaction().begin();
        em.persist(es);
        em.getTransaction().commit();
        em.close();
    }
    //recuperar un estudiante, en base a su número de libreta universitaria
    public EstudianteFiltradoDTO obtenerEstudianteSegunLU(int lu) {
        EntityManager em = JPAUtil.getEntityManager();
        em.getTransaction().begin();
        Query query = em.createQuery("SELECT new DTO.EstudianteFiltradoDTO( e.dni, e.nombre, e.apellido, e.genero, e.edad, e.ciudad, e.LU) FROM Estudiante e WHERE e.LU = :lu");
        query.setParameter("lu", lu);
        List<EstudianteFiltradoDTO> es = query.getResultList();
        return es.get(0);
    }
    public List<EstudianteFiltradoDTO> obtenerEstudiantes(){
        EntityManager em = JPAUtil.getEntityManager();
        String jpql = "SELECT new DTO.EstudianteFiltradoDTO( e.dni, e.nombre, e.apellido, e.genero, e.edad, e.ciudad, e.LU) FROM Estudiante e ORDER BY nombre ASC";
        Query query = em.createQuery(jpql);
        List<EstudianteFiltradoDTO> estudiantes = query.getResultList();
        return estudiantes;
    }
    public List<EstudianteFiltradoDTO> estudiantesByGenero(String genero){
        EntityManager em = JPAUtil.getEntityManager();
        Query query = em.createQuery("SELECT new DTO.EstudianteFiltradoDTO( e.dni, e.nombre, e.apellido, e.genero, e.edad, e.ciudad, e.LU) FROM Estudiante e WHERE e.genero = :genero").setParameter("genero", genero);
        List<EstudianteFiltradoDTO> estudiantes = query.getResultList();
        return estudiantes;
    }
    public List<EstudianteFiltradoDTO> estudiantesByCarrera(Carrera carreraObj, String ciudad){
        int id_carrera = carreraObj.getId_carrera();
        EntityManager em = JPAUtil.getEntityManager();
        Query query = em.createQuery("SELECT new DTO.EstudianteFiltradoDTO( e.dni, e.nombre, e.apellido, e.genero, e.edad, e.ciudad, e.LU) FROM Estudiante e JOIN e.carreras c  WHERE c.id_carrera = :carrera AND e.ciudad LIKE :ciudad");
        query.setParameter("carrera", carreraObj).setParameter("ciudad", ciudad);
        List<EstudianteFiltradoDTO> estudiantes = query.getResultList();
        return estudiantes;
    }
}