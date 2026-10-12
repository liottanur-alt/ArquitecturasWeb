package Servicios.DTO.Carrera.Req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CarrerasDTOReq {

    @NotNull (message = "El nombre es obligatoriuo")
    @NotEmpty (message = "El nombre es obligatoriuo")
    private String nombreCarrera;
    private int duracion;
}
