package Servicios.DTO.EstudianteCarrera.Res;


import Entities.EstudianteCarrera;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@Data
@RequiredArgsConstructor
public class EstudianteCarreraResponseDTO {

    private final Long idEstudiante;
    private final Integer idCarrera;
    private final LocalDate inscripcion;
    private final LocalDate graduacion;

    public EstudianteCarreraResponseDTO(EstudianteCarrera ec) {
        this.idEstudiante = ec.getId().getIdEstudiante();
        this.idCarrera = ec.getId().getIdCarrera();
        this.inscripcion = ec.getInscripcion();
        this.graduacion = ec.getGraduacion();
    }
}

