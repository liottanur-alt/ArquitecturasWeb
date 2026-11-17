package com.universidad.msvsectoerasiento.Entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "asientos", uniqueConstraints = @UniqueConstraint(name = "uk_asiento_posicion", columnNames = {"id_sector", "fila", "columna"}))
public class Asiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_sector", nullable = false)
    private Sector sector;

    @Column(nullable = false)
    private String fila;

    @Column(nullable = false)
    private String columna;

}
