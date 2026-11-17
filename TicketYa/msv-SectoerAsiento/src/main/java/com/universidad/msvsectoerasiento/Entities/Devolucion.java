package com.universidad.msvsectoerasiento.Entities;

import jakarta.persistence.*;
import lombok.*;

/** Conserva el resultado de cada devolucion para que un reintento no libere cupos de nuevo. */
@Entity @Table(name = "devoluciones_inventario")
@Getter @Setter @NoArgsConstructor
public class Devolucion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 100) private String clave;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) private Reserva reserva;
    @Column(nullable = false) private Integer cantidad;
    @Column(nullable = false) private java.time.Instant solicitadaEn;
    @Column(nullable = false, length = 21000) private String posiciones;
}
