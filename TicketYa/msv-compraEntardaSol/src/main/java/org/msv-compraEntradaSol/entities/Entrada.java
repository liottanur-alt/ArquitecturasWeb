package msv_compraEntradaSol.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "entrada")
public class Entrada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String qr;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Estadoqr estadoqr;

    @ManyToOne
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "id_evento")
    private Evento evento;

    @ManyToOne
    @JoinColumn(name = "id_sector")
    private Sector sector;

    @OneToOne
    @JoinColumn(name = "id_asiento")
    private Asiento asiento;

    @Column(nullable = false)
    private Long precio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoEntrada estadoEntrada;

    public Long getId() { return id; }

    public String getQr() { return qr; }
    public void setQr(String qr) { this.qr = qr; }

    public Estadoqr getEstadoqr() { return estadoqr; }
    public void setEstadoqr(Estadoqr estadoqr) { this.estadoqr = estadoqr; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

    public Evento getEvento() { return evento; }
    public void setEvento(Evento evento) { this.evento = evento; }

    public Sector getSector() { return sector; }
    public void setSector(Sector sector) { this.sector = sector; }

    public Asiento getAsiento() { return asiento; }
    public void setAsiento(Asiento asiento) { this.asiento = asiento; }

    public Long getPrecio() { return precio; }
    public void setPrecio(Long precio) { this.precio = precio; }

    public EstadoEntrada getEstadoEntrada() { return estadoEntrada; }
    public void setEstadoEntrada(EstadoEntrada estadoEntrada) { this.estadoEntrada = estadoEntrada; }
}