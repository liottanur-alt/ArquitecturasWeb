package entity;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
public class Producto {
    private int id;
    private String nombre;
    private float valor;
}