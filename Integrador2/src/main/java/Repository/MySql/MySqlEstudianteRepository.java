package Repository.MySql;

import DTO.*;
import Entities.Estudiante;
import Factory.JPAUtil;
import Repository.RepoInterfaz;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.time.LocalDate;
import java.util.List;

public class MySqlEstudianteRepository implements RepoInterfaz<Estudiante, Long> {

    // =========================================================================
    // CARGA MASIVA DESDE CSV
    // =========================================================================
    public void cargarDesdeCsv() {
        EntityManager em = JPAUtil.getEntityManager();

        try (FileReader reader = new FileReader("Integrador2/src/main/resources/Estudiante.csv");
             CSVParser parser = CSVFormat.DEFAULT.withHeader().parse(reader)) {

            em.getTransaction().begin();

            for (CSVRecord row : parser) {
                Long dni = Long.parseLong(row.get("DNI"));
                String nombre = row.get("nombre");
                String apellido = row.get("apellido");
                int edadCsv = Integer.parseInt(row.get("edad"));
                String genero = row.get("genero");
                String ciudad = row.get("ciudad");
                int lu = Integer.parseInt(row.get("LU"));

                // Instancia la entidad calculando la fechaNacimiento aproximada en el constructor
                Estudiante estudiante = new Estudiante(dni, nombre, apellido, edadCsv, genero, ciudad, lu);
                em.persist(estudiante);
            }

            em.getTransaction().commit();
            System.out.println("Estudiantes procesados e insertados desde el CSV correctamente.");

        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            System.err.println("Error al cargar estudiantes desde CSV: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    // =========================================================================
    // MÉTODOS BASE DE RepoInterfaz<Estudiante>
    // =========================================================================

    // a) Dar de alta un estudiante[cite: 1]
    @Override
    public void guardar(Estudiante estudiante) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            em.persist(estudiante);
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    @Override
    public Estudiante buscarPorId(Long dni) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            return em.find(Estudiante.class, dni);
        } finally {
            em.close();
        }
    }

    // c) Recuperar todos los estudiantes ordenados[cite: 1]
    @Override
    public List<Estudiante> buscarTodos() {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            String jpql = "SELECT e FROM Estudiante e ORDER BY e.apellido ASC";
            return em.createQuery(jpql, Estudiante.class).getResultList();
        } finally {
            em.close();
        }
    }

    @Override
    public void eliminar(Estudiante estudiante) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            Estudiante entidad = em.merge(estudiante);
            em.remove(entidad);
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    // =========================================================================
    // CONSULTAS JPQL QUE RETORNAN DIRECTAMENTE EstudianteDTO
    // =========================================================================

    // c) Recuperar todos los estudiantes ordenados alfabéticamente por apellido (versión DTO)[cite: 1]
    public List<EstudianteDTO> buscarTodosDTO() {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            String jpql = "SELECT new DTO.EstudianteDTO(e.dni, e.nombre, e.apellido, e.genero, e.ciudad, e.lu, e.fechaNacimiento) " +
                    "FROM Estudiante e ORDER BY e.apellido ASC";
            return em.createQuery(jpql, EstudianteDTO.class).getResultList();
        } finally {
            em.close();
        }
    }

    // d) Recuperar un estudiante en base a su número de libreta universitaria (LU)[cite: 1]
    public EstudianteDTO buscarPorLUDTO(int lu) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            String jpql = "SELECT new DTO.EstudianteDTO(e.dni, e.nombre, e.apellido, e.genero, e.ciudad, e.lu, e.fechaNacimiento) " +
                    "FROM Estudiante e WHERE e.lu = :lu";
            TypedQuery<EstudianteDTO> query = em.createQuery(jpql, EstudianteDTO.class);
            query.setParameter("lu", lu);
            return query.getSingleResult();
        } finally {
            em.close();
        }
    }

    // e) Recuperar todos los estudiantes en base a su género[cite: 1]
    public List<EstudianteDTO> buscarPorGeneroDTO(String genero) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            String jpql = "SELECT new DTO.EstudianteDTO(e.dni, e.nombre, e.apellido, e.genero, e.ciudad, e.lu, e.fechaNacimiento) " +
                    "FROM Estudiante e WHERE e.genero = :genero";
            TypedQuery<EstudianteDTO> query = em.createQuery(jpql, EstudianteDTO.class);
            query.setParameter("genero", genero);
            return query.getResultList();
        } finally {
            em.close();
        }
    }

    // g) Recuperar los estudiantes de una determinada carrera, filtrado por ciudad de residencia[cite: 1]
    public List<EstudianteDTOCarreraYCiudad> buscarPorCarreraYCiudadDTO(int idCarrera, String ciudad) {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            String jpql = "SELECT new DTO.EstudianteDTOCarreraYCiudad(" +
                    "ec.estudiante.dni, " +
                    "ec.estudiante.nombre, " +
                    "ec.estudiante.apellido, " +
                    "ec.estudiante.genero, " +
                    "ec.estudiante.ciudad, " +
                    "ec.estudiante.lu, " +
                    "ec.estudiante.fechaNacimiento) " +
                    "FROM EstudianteCarrera ec " +
                    "WHERE ec.carrera.idCarrera = :idCarrera " +
                    "AND ec.estudiante.ciudad = :ciudad";

            TypedQuery<EstudianteDTOCarreraYCiudad> query = em.createQuery(jpql, EstudianteDTOCarreraYCiudad.class);
            query.setParameter("idCarrera", idCarrera);
            query.setParameter("ciudad", ciudad);
            return query.getResultList();
        } finally {
            em.close();
        }
    }
}
