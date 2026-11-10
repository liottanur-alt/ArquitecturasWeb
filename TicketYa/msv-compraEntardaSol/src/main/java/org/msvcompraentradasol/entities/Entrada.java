package org.msvcompraentradasol.entities;

import jakarta.persistence.*;
import java.math.BigDecimal;
import org.msvcompraentradasol.enums.EstadoEntrada;
import org.msvcompraentradasol.enums.Estadoqr;

@Entity
@Table(
        name = "entrada",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_entrada_evento_asiento",
                columnNames = {"id_evento", "id_asiento"}
        )
)
public class Entrada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String qr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estadoqr estadoqr;

    @Column(name = "id_cliente")
    private Long idCliente;

    @Column(name = "id_evento")
    private Long idEvento;

    @Column(name = "id_sector")
    private Long idSector;

    @Column(name = "id_asiento")
    private Long idAsiento;

    @Column(nullable = false)
    private BigDecimal precio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoEntrada estadoEntrada;

    @ManyToOne
    @JoinColumn(name = "id_compra", nullable = false)
    private Compra compra;

    public Compra getCompra() { return compra; }
    public void setCompra(Compra compra) { this.compra = compra; }

    public Long getId() { return id; }

    public String getQr() { return qr; }
    public void setQr(String qr) { this.qr = qr; }

    public Estadoqr getEstadoqr() { return estadoqr; }
    public void setEstadoqr(Estadoqr estadoqr) { this.estadoqr = estadoqr; }

    public Long getIdCliente() { return idCliente; }
    public void setIdCliente(Long idCliente) { this.idCliente = idCliente; }

    public Long getIdEvento() { return idEvento; }
    public void setIdEvento(Long idEvento) { this.idEvento = idEvento; }

    public Long getIdSector() { return idSector; }
    public void setIdSector(Long idSector) { this.idSector = idSector; }

    public Long getIdAsiento() { return idAsiento; }
    public void setIdAsiento(Long idAsiento) { this.idAsiento = idAsiento; }

    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }

    public EstadoEntrada getEstadoEntrada() { return estadoEntrada; }
    public void setEstadoEntrada(EstadoEntrada estadoEntrada) { this.estadoEntrada = estadoEntrada; }
}
