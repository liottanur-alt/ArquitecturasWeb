package main.java.Entities;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "estudianteCarrera")
public class EstudianteCarrera {

    @EmbeddedId
    private EstudianteCarreraPK id;

    @ManyToOne
    @MapsId("idEstudiante")
    @JoinColumn(name = "id_estudiante", nullable = false)
    private main.java.Entities.Estudiante estudiante;

    @ManyToOne
    @MapsId("idCarrera")
    @JoinColumn(name = "id_carrera", nullable = false)
    private Carrera carrera;

    @Column(name = "inscripcion", nullable = false)
    private LocalDate inscripcion;

    @Column(name = "graduacion")
    private LocalDate graduacion;



    public EstudianteCarrera(EstudianteCarreraPK id, Estudiante estudiante,
                             Carrera carrera, LocalDate inscripcion,
                             LocalDate graduacion) {
        this.id = id;
        this.estudiante = estudiante;
        this.carrera = carrera;
        this.inscripcion = inscripcion;
        this.graduacion = graduacion;
    }

    public EstudianteCarrera() {}

    public EstudianteCarreraPK getId() {
        return id;
    }

    public void setId(EstudianteCarreraPK id) {
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

    public LocalDate getInscripcion() {
        return inscripcion;
    }

    public void setInscripcion(LocalDate inscripcion) {
        this.inscripcion = inscripcion;
    }

    public LocalDate getGraduacion() {
        return graduacion;
    }

    public void setGraduacion(LocalDate graduacion) {
        this.graduacion = graduacion;
    }


    @Override
    public String toString() {
        return "EstudianteCarrera{" +
                "id=" + id +
                ", estudiante=" + estudiante +
                ", carrera=" + carrera +
                ", inscripcion=" + inscripcion +
                ", graduacion=" + graduacion +
                '}';
    }
}