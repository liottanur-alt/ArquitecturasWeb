package com.universidad.msvsectoerasiento.Entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.universidad.msvsectoerasiento.enums.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "asientos_evento", uniqueConstraints = @UniqueConstraint(name = "uk_asiento_evento", columnNames = {"id_sector_evento", "id_asiento"}))
public class AsientoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_sector_evento", nullable = false)
    private SectorEvento sectorEvento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_asiento", nullable = false)
    private Asiento asiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoAsiento estado = EstadoAsiento.DISPONIBLE;

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

}
