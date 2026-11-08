package com.universidad.msvsectoerasiento.Entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Check(constraints = "precio >= 0 and capacidad_habilitada > 0 and lugares_reservados >= 0 and lugares_vendidos >= 0 and lugares_reservados + lugares_vendidos <= capacidad_habilitada")
@Table(name = "sectores_evento", uniqueConstraints = @UniqueConstraint(name = "uk_sector_evento", columnNames = {"id_evento", "id_sector"}))
public class SectorEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_evento", nullable = false)
    private Long idEvento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_sector", nullable = false)
    private Sector sector;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false)
    private Integer capacidadHabilitada;

    @Column(nullable = false)
    private Integer lugaresReservados = 0;

    @Column(nullable = false)
    private Integer lugaresVendidos = 0;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

    /** Cupo de campo; en sectores numerados se consulta AsientoEvento. */
    @Transient
    public int getEspaciosDisponibles() {
        return capacidadHabilitada - lugaresReservados - lugaresVendidos;
    }
}
