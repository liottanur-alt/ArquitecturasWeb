package com.universidad.msvsectoerasiento.Entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Cabecera bloqueable: coordina reservas y cancelaciones de todos los sectores del evento. */
@Entity @Table(name = "inventarios_evento")
@Getter @Setter @NoArgsConstructor
public class InventarioEvento {
    @Id private Long id;
    @Column(nullable = false) private Long idLugar;
    @Column(nullable = false) private LocalDateTime fechaHora;
    @Column(nullable = false) private Integer limitePorCliente;
    @Column(nullable = false) private boolean cancelado;
}
