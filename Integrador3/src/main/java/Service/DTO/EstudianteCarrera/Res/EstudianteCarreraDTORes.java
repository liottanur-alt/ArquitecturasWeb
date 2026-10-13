package Service.DTO.EstudianteCarrera.Res;


import Entities.Estudiante;
import Entities.EstudianteCarrera;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;



import Entities.EstudianteCarrera;
import java.time.LocalDate;

public class EstudianteCarreraDTORes {

    private Long idEstudiante;
    private Integer idCarrera;
    private LocalDate inscripcion;
    private LocalDate graduacion;


    // Constructor con todos los campos (requerido por consultas JPQL "SELECT new ...")
    public EstudianteCarreraDTORes(Long idEstudiante, Integer idCarrera, LocalDate inscripcion, LocalDate graduacion) {
        this.idEstudiante = idEstudiante;
        this.idCarrera = idCarrera;
        this.inscripcion = inscripcion;
        this.graduacion = graduacion;
    }

    // Constructor de conveniencia (para mapear directamente desde la Entidad en tus servicios)
    public EstudianteCarreraDTORes(EstudianteCarrera ec) {
        if (ec != null) {
            this.idEstudiante = (ec.getId() != null) ? ec.getId().getIdEstudiante() : null;
            this.idCarrera = (ec.getId() != null) ? ec.getId().getIdCarrera() : null;
            this.inscripcion = ec.getInscripcion();
            this.graduacion = ec.getGraduacion();
        }
    }


    public EstudianteCarreraDTORes(EstudianteCarreraDTORes dto) {
        if (dto != null) {
            this.idEstudiante = dto.getIdEstudiante();
            this.idCarrera = dto.getIdCarrera();
            this.inscripcion = dto.getInscripcion();
            this.graduacion = dto.getGraduacion();
        }
    }

    // --- Getters y Setters ---

    public Long getIdEstudiante() {
        return idEstudiante;
    }

    public void setIdEstudiante(Long idEstudiante) {
        this.idEstudiante = idEstudiante;
    }

    public Integer getIdCarrera() {
        return idCarrera;
    }

    public void setIdCarrera(Integer idCarrera) {
        this.idCarrera = idCarrera;
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
        return "EstudianteCarreraDTORes{" +
                "idEstudiante=" + idEstudiante +
                ", idCarrera=" + idCarrera +
                ", inscripcion=" + inscripcion +
                ", graduacion=" + graduacion +
                '}';
    }
}

