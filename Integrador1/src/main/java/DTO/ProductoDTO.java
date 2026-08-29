package DTO;

import lombok.Data;

@Data
public class ProductoDTO {
    private String nombre;
    private float valor;
    private float recaudacion;

    public ProductoDTO(String nombre, float valor, float recaudacion) {
        this.nombre = nombre;
        this.valor = valor;
        this.recaudacion = recaudacion;
    }
}
