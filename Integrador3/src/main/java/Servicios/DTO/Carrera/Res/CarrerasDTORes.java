package Servicios.DTO.Carrera.Res;

import Entities.Carrera;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class CarrerasDTORes {

    private int idCarrera;
    private String nombreCarrera;
    private int duracion;

    public CarrerasDTORes(Carrera carrera){
        this.idCarrera = carrera.getIdCarrera();
        this.nombreCarrera = carrera.getNombreCarrera();
        this.duracion = carrera.getDuracion();
    }
}
