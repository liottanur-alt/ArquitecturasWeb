package main.java.DTO;

import java.time.LocalDate;

public class EstudianteCarreraDTO {

    private long dniEstudiante;
    private int idCarrera;
    private LocalDate inscripcion;
    private LocalDate graduacion;

    public EstudianteCarreraDTO(long dniEstudiante, int idCarrera,
                                LocalDate inscripcion, LocalDate graduacion) {
        this.dniEstudiante = dniEstudiante;
        this.idCarrera = idCarrera;
        this.inscripcion = inscripcion;
        this.graduacion = graduacion;
    }

    // Getters

    public long getDniEstudiante() {
        return dniEstudiante;
    }

    public int getIdCarrera() {
        return idCarrera;
    }

    public LocalDate getInscripcion() {
        return inscripcion;
    }

    public LocalDate getGraduacion() {
        return graduacion;
    }

    // Setters

    public void setDniEstudiante(long dniEstudiante) {
        this.dniEstudiante = dniEstudiante;
    }

    public void setIdCarrera(int idCarrera) {
        this.idCarrera = idCarrera;
    }

    public void setInscripcion(LocalDate inscripcion) {
        this.inscripcion = inscripcion;
    }

    public void setGraduacion(LocalDate graduacion) {
        this.graduacion = graduacion;
    }
}