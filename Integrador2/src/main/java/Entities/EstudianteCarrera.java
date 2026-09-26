package Entities;

import jakarta.persistence.*;
import entities.Estudiante;
import entities.Carrera;
@Entity
@Table(name = "estudianteCarrera")
public class EstudianteCarrera {
//id,estudiante,carrera,inscripcion,graduacion,antiguedad
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "id_estudiante", nullable = false)
    private Estudiante estudiante;

    @ManyToOne
    @JoinColumn(name = "id_carrera", nullable = false)
    private Carrera carrera;

    @Column(name = "inscripcion", nullable = false)
    private int inscripcion;

    @Column(name = "graduacion")
    private int graduacion;

    @Column(name = "antiguedad", nullable = false)
    private int antiguedad;

    public EstudianteCarrera(int id, Estudiante estudiante, Carrera carrera, int inscripcion, int graduacion, int antiguedad) {
        this.id = id;
        this.estudiante = estudiante;
        this.carrera = carrera;
        this.inscripcion = inscripcion;
        this.graduacion = graduacion;
        this.antiguedad = antiguedad;
    }

    public EstudianteCarrera() {}

    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }

    public Estudiante getEstudiante() {
        return estudiante;
    }
    public void setEstudiante(Estudiante estudiante) {
        this.estudiante = estudiante;
    }

    public Carrera getCarrera() {
        return carrera;
    }
    public void setCarrera(Carrera carrera) {
        this.carrera = carrera;
    }

    public int getInscripcion() {
        return inscripcion;
    }
    public void setInscripcion(int inscripcion) {
        this.inscripcion = inscripcion;
    }

    public int getGraduacion() {
        return graduacion;
    }
    public void setGraduacion(int graduacion) {
        this.graduacion = graduacion;
    }

    public int getAntiguedad() {
        return antiguedad;
    }
    public void setAntiguedad(int antiguedad) {
        this.antiguedad = antiguedad;
    }

    @Override
    public String toString() {
        return "EstudianteCarrera{" + "id" + id + ", id Estudiante='" +
                estudiante + '\'' + ", id carrera=" + carrera + ", inscripcion=" + inscripcion +
                ", graduacion=" + graduacion + ", antiguedad=" + antiguedad + '}';
    }
}
