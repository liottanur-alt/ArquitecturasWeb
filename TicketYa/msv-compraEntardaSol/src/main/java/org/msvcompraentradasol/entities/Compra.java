package msv_compraEntradaSol.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "compra")
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_cliente")
    private Cliente cliente;

    @Column(nullable = false)
    private String medioDePago;

    @ManyToOne
    @JoinColumn(name = "id_evento")
    private Evento evento;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false)
    private Long cantidadEntradas;

    public Long getId() { return id; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

    public String getMedioDePago() { return medioDePago; }
    public void setMedioDePago(String medioDePago) { this.medioDePago = medioDePago; }

    public Evento getEvento() { return evento; }
    public void setEvento(Evento evento) { this.evento = evento; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Long getCantidadEntradas() { return cantidadEntradas; }
    public void setCantidadEntradas(Long cantidadEntradas) { this.cantidadEntradas = cantidadEntradas; }
}