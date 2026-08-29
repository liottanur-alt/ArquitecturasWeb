package entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@NoArgsConstructor 
@AllArgsConstructor
public class FacturaProducto {
    private int idFactura;
    private int idProducto;
    private int cantidad;
}