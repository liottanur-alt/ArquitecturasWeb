package org.msvcompraentradasol.entities;

import jakarta.persistence.*;
import org.msvcompraentradasol.enums.EstadoCompra;
import org.msvcompraentradasol.enums.EstadoReintegro;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "compra")
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_cliente", nullable = false)
    private Long idCliente;

    @Column(name = "medio_de_pago", nullable = false)
    private String medioDePago;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_compra", nullable = false)
    private EstadoCompra estadoCompra;

    @Column(name = "id_evento", nullable = false)
    private Long idEvento;

    @Column(nullable = false)
    private String descripcion;

    @Column(name = "cantidad_entradas", nullable = false)
    private Long cantidadEntradas;

    @Column(name = "id_sector", nullable = false)
    private Long idSector;

    @Column(name = "clave_idempotencia", nullable = false, unique = true, length = 100)
    private String claveIdempotencia;

    @Column(name = "id_reserva", nullable = false)
    private Long idReserva;

    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;

    @Column(name = "importe_total", nullable = false)
    private BigDecimal importeTotal;

    @Column(name = "referencia_pago")
    private String referenciaPago;

    @Column(name = "referencia_reintegro")
    private String referenciaReintegro;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_reintegro")
    private EstadoReintegro estadoReintegro;

    @ElementCollection
    @CollectionTable(name = "compra_asientos", joinColumns = @JoinColumn(name = "id_compra"))
    @Column(name = "id_asiento")
    private List<Long> idsAsientos = new ArrayList<>();



    public Long getId() { return id; }

    public Long getIdCliente() { return idCliente;}
    public void setIdCliente(Long idCliente) {this.idCliente = idCliente;}

    public String getMedioDePago() {return medioDePago;}
    public void setMedioDePago(String medioDePago) {this.medioDePago = medioDePago;}

    public Long getIdEvento() {return idEvento;}
    public void setIdEvento(Long idEvento) {this.idEvento = idEvento;}

    public String getDescripcion() {return descripcion;}
    public void setDescripcion(String descripcion) {this.descripcion = descripcion;}

    public Long getCantidadEntradas() {return cantidadEntradas;}
    public void setCantidadEntradas(Long cantidadEntradas) {this.cantidadEntradas = cantidadEntradas;}

    public EstadoCompra getEstadoCompra() {return estadoCompra;}
    public void setEstadoCompra(EstadoCompra estadoCompra) {this.estadoCompra = estadoCompra;}

    public Long getIdSector() {
        return idSector;
    }
    public void setIdSector(Long idSector) {
        this.idSector = idSector;
    }

    public String getClaveIdempotencia() {
        return claveIdempotencia;
    }
    public void setClaveIdempotencia(String claveIdempotencia) {
        this.claveIdempotencia = claveIdempotencia;
    }

    public Long getIdReserva() {
        return idReserva;
    }
    public void setIdReserva(Long idReserva) {
        this.idReserva = idReserva;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }
    public void setPrecioUnitario(BigDecimal precioUnitario) {
        this.precioUnitario = precioUnitario;
    }

    public BigDecimal getImporteTotal() {
        return importeTotal;
    }
    public void setImporteTotal(BigDecimal importeTotal) {
        this.importeTotal = importeTotal;
    }

    public String getReferenciaPago() {
        return referenciaPago;
    }
    public void setReferenciaPago(String referenciaPago) {
        this.referenciaPago = referenciaPago;
    }

    public String getReferenciaReintegro() {
        return referenciaReintegro;
    }
    public void setReferenciaReintegro(String referenciaReintegro) {
        this.referenciaReintegro = referenciaReintegro;
    }

    public EstadoReintegro getEstadoReintegro() {
        return estadoReintegro;
    }
    public void setEstadoReintegro(EstadoReintegro estadoReintegro) {
        this.estadoReintegro = estadoReintegro;
    }

    public List<Long> getIdsAsientos() {
        return idsAsientos;
    }
    public void setIdsAsientos(List<Long> idsAsientos) {
        this.idsAsientos = idsAsientos;
    }
}
