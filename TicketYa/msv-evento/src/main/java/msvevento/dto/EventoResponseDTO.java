
package msvevento.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import msvevento.entity.EstadoEvento;
import msvevento.entity.EstadoPublicacion;
import msvevento.entity.TipoEvento;

import java.time.LocalDateTime;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventoResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = -3558115216547538520L;

    private Long id;
    private String nombre;
    private String descripcion;
    private TipoEvento tipo;
    private Long idOrganizador;
    private List<ArtistaResponseDTO> participantes;
    private LocalDateTime fechaHora;
    private LugarResponseDTO lugar;
    private EstadoEvento estado;
    private EstadoPublicacion estadoPublicacion;
    private boolean demanda;
    private String imagenUrl;
}
