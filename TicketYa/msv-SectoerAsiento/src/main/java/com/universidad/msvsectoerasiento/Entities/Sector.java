package com.universidad.msvsectoerasiento.Entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import com.universidad.msvsectoerasiento.enums.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Check(constraints = "capacidad_total > 0")
@Table(name = "sectores")
public class Sector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_lugar", nullable = false)
    private Long idLugar;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoSector tipo;

    @Column(nullable = false)
    private Integer capacidadTotal;

}
