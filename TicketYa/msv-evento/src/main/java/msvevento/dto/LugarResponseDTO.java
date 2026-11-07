
package msvevento.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import msvevento.entity.TipoLugar;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LugarResponseDTO {

    private Long id;
    private String nombre;
    private String direccion;
    private Double latitud;
    private Double longitud;
    private String ciudad;
    private TipoLugar tipo;
}
