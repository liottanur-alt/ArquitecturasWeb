package Repository.MySql;

import DTO.CarreraDTO;
import Entities.Carrera;
import Factory.JPAUtil;
import Repository.RepoInterfaz;
import jakarta.persistence.EntityManager;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class MySqlCarreraRepository implements RepoInterfaz<Carrera, Integer> {

    @Override
    public void guardar(Carrera objeto) {
        EntityManager em = JPAUtil.getEntityManager();
        em.getTransaction().begin();
        em.persist(objeto);
        em.getTransaction().commit();
        em.close();
    }

    @Override
    public Carrera buscarPorId(Integer id) {
        EntityManager em = JPAUtil.getEntityManager();
        Carrera carrera = em.find(Carrera.class, id);
        em.close();
        return carrera;
    }


    @Override
    public List<Carrera> buscarTodos() {
        EntityManager em = JPAUtil.getEntityManager();
        List<Carrera> lista = em.createQuery(
                "SELECT c FROM Carrera c",
                Carrera.class
        ).getResultList();
        em.close();
        return lista;
    }

    @Override
    public void eliminar(Carrera objeto) {
        EntityManager em = JPAUtil.getEntityManager();
        em.getTransaction().begin();
        Carrera entidad = em.merge(objeto);
        em.remove(entidad);
        em.getTransaction().commit();
        em.close();
    }

    // 2.f) Recuperar las carreras con estudiantes inscriptos, y ordenar por cantidad de inscriptos.
    public List<Carrera> getCarrerasConInscriptos() {
        EntityManager em = JPAUtil.getEntityManager();
        String jpql = "SELECT c " +
                "FROM EstudianteCarrera ec " +
                "JOIN ec.carrera c " +
                "GROUP BY c " +
                "ORDER BY COUNT(ec.estudiante) DESC";

        List<Carrera> resultado = em.createQuery(jpql, Carrera.class).getResultList();
        em.close();
        return resultado;
    }

    // 3) Generar un reporte de las carreras (inscriptos y egresados por año).
    // Se ordenan alfabéticamente por carrera y cronológicamente por año.
    public List<CarreraDTO> generarReporte() {
        EntityManager em = JPAUtil.getEntityManager();
        try {
            String jpql = "SELECT new DTO.CarreraDTO(" +
                    "c.nombreCarrera, " +
                    "YEAR(ec.inscripcion), " +
                    "COUNT(ec.estudiante), " +
                    "SUM(CASE WHEN ec.graduacion IS NOT NULL THEN 1L ELSE 0L END)) " +
                    "FROM EstudianteCarrera ec " +
                    "JOIN ec.carrera c " +
                    "GROUP BY c.nombreCarrera, YEAR(ec.inscripcion) " +
                    "ORDER BY c.nombreCarrera ASC, YEAR(ec.inscripcion) ASC";

            return em.createQuery(jpql, CarreraDTO.class).getResultList();
        } finally {
            em.close();
        }
    }
    public void insertarDatosCsv() {
        InputStream stream = getClass().getResourceAsStream("/carreras.csv");
        if (stream == null) {
            throw new IllegalStateException("No se encontró el recurso carreras.csv");
        }
        insertarDatosCsv(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    public void insertarDatosCsv(String rutaArchivo) {
        try {
            insertarDatosCsv(Files.newBufferedReader(Paths.get(rutaArchivo), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Error leyendo CSV de Carreras: " + rutaArchivo, e);
        }
    }

    private void insertarDatosCsv(Reader reader) {
        try (CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {
            ArrayList<Carrera> carreras = new ArrayList<>();
            for (CSVRecord row : parser) {
                int idCarrera = Integer.parseInt(row.get("id_carrera"));
                String nombreCarrera = row.get("carrera");
                int duracion = Integer.parseInt(row.get("duracion"));

                carreras.add(new Carrera(idCarrera, nombreCarrera, duracion));
            }
            this.insertarDatos(carreras);
        } catch (IOException e) {
            throw new IllegalStateException("Error leyendo CSV de Carreras", e);
        }
    }

    public void insertarDatos(ArrayList<Carrera> carreras) {
        EntityManager em = JPAUtil.getEntityManager();

        try {
            em.getTransaction().begin();

            for (Carrera c : carreras) {
                em.merge(c);
            }

            em.getTransaction().commit();

            System.out.println("Datos de Carrera cargados con éxito!");

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
