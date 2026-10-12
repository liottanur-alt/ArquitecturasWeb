
package Servicios.DTO.EstudianteCarrera.Req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EstudianteCarreraRequestDTO {

    @NotNull(message = "El estudiante es obligatorio.")
    private Long idEstudiante;

    @NotNull(message = "La carrera es obligatoria.")
    private Integer idCarrera;

    @NotNull(message = "La fecha de inscripción es obligatoria.")
    private LocalDate inscripcion;

    private LocalDate graduacion;
}

