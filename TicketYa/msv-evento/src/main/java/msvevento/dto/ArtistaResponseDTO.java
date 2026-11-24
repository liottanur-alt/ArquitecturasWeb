package msvevento.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ArtistaResponseDTO implements java.io.Serializable {
    private Long id;
    private String nombre;
    private String genero;
}
