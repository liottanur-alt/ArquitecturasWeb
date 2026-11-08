package com.universidad.msvsectoerasiento.Entities;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import com.universidad.msvsectoerasiento.enums.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Check(constraints = "cantidad > 0 and precio_unitario >= 0 and vence_en > creada_en and cantidad_devuelta >= 0 and cantidad_devuelta <= cantidad")
@Table(name = "reservas", indexes = @Index(name = "ix_reservas_vencimiento", columnList = "estado,vence_en"))
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String claveIdempotencia;

    @Column(nullable = false)
    private Long idCliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_sector_evento", nullable = false)
    private SectorEvento sectorEvento;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false)
    private Instant creadaEn = Instant.now();

    @Column(name = "vence_en", nullable = false)
    private Instant venceEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoReserva estado = EstadoReserva.ACTIVA;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "reservas_asientos",
        joinColumns = @JoinColumn(name = "id_reserva"),
        inverseJoinColumns = @JoinColumn(name = "id_asiento_evento"),
        uniqueConstraints = @UniqueConstraint(name = "uk_reserva_asiento", columnNames = {"id_reserva", "id_asiento_evento"}))
    private List<AsientoEvento> asientos = new ArrayList<>();

    // Acumula devoluciones sin perder la composicion original de la compra.
    @Column(nullable = false)
    private Integer cantidadDevuelta = 0;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "reservas_asientos_devueltos",
        joinColumns = @JoinColumn(name = "id_reserva"),
        inverseJoinColumns = @JoinColumn(name = "id_asiento_evento"))
    private List<AsientoEvento> asientosDevueltos = new ArrayList<>();

    @Version
    @Setter(AccessLevel.NONE)
    private Long version;

}
