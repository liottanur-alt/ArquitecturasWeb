
package Service.DTO.EstudianteCarrera.Req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EstudianteCarreraDTOReq {

    @NotNull(message = "El estudiante es obligatorio.")
    private Long idEstudiante;

    @NotNull(message = "La carrera es obligatoria.")
    private Integer idCarrera;

    @NotNull(message = "La fecha de inscripción es obligatoria.")
    private LocalDate inscripcion;

    public @NotNull(message = "El estudiante es obligatorio.") Long getIdEstudiante() {
        return idEstudiante;
    }

    public void setIdEstudiante(@NotNull(message = "El estudiante es obligatorio.") Long idEstudiante) {
        this.idEstudiante = idEstudiante;
    }

    public @NotNull(message = "La carrera es obligatoria.") Integer getIdCarrera() {
        return idCarrera;
    }

    public void setIdCarrera(@NotNull(message = "La carrera es obligatoria.") Integer idCarrera) {
        this.idCarrera = idCarrera;
    }

    public LocalDate getGraduacion() {
        return graduacion;
    }

    public void setGraduacion(LocalDate graduacion) {
        this.graduacion = graduacion;
    }

    public @NotNull(message = "La fecha de inscripción es obligatoria.") LocalDate getInscripcion() {
        return inscripcion;
    }

    public void setInscripcion(@NotNull(message = "La fecha de inscripción es obligatoria.") LocalDate inscripcion) {
        this.inscripcion = inscripcion;
    }

    private LocalDate graduacion;
}

