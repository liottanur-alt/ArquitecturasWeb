package com.msvnotificaciones.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "sucesos_negocio", indexes = {
    @Index(name = "idx_suceso_evento_tipo", columnList = "evento_id,tipo"),
    @Index(name = "idx_suceso_compra", columnList = "compra_id")
})
public class SucesoNegocio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String claveIdempotencia;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private TipoSuceso tipo;
    @Column(name = "evento_id", nullable = false)
    private Long idEvento;
    @Column(name = "compra_id")
    private Long idCompra;
    @Column(name = "cliente_id")
    private Long idCliente;
    @Column(name = "destinatario", length = 320)
    private String destinatario;
    @Column(name = "cantidad_entradas")
    private Integer cantidadEntradas;
    @Column(precision = 14, scale = 2)
    private BigDecimal importe;
    @Column(name = "capacidad_total")
    private Integer capacidadTotal;
    @Column(name = "fecha_suceso", nullable = false)
    private Instant fechaSuceso;
    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro;

    protected SucesoNegocio() {}

    public SucesoNegocio(String claveIdempotencia, TipoSuceso tipo, Long idEvento, Long idCompra,
                         Long idCliente, String destinatario, Integer cantidadEntradas,
                         BigDecimal importe, Integer capacidadTotal, Instant fechaSuceso) {
        this.claveIdempotencia = claveIdempotencia;
        this.tipo = tipo;
        this.idEvento = idEvento;
        this.idCompra = idCompra;
        this.idCliente = idCliente;
        this.destinatario = destinatario;
        this.cantidadEntradas = cantidadEntradas;
        this.importe = importe;
        this.capacidadTotal = capacidadTotal;
        this.fechaSuceso = fechaSuceso;
        this.fechaRegistro = Instant.now();
    }

    public Long getId() { return id; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
    public TipoSuceso getTipo() { return tipo; }
    public Long getIdEvento() { return idEvento; }
    public Long getIdCompra() { return idCompra; }
    public Long getIdCliente() { return idCliente; }
    public String getDestinatario() { return destinatario; }
    public Integer getCantidadEntradas() { return cantidadEntradas; }
    public BigDecimal getImporte() { return importe; }
    public Integer getCapacidadTotal() { return capacidadTotal; }
    public Instant getFechaSuceso() { return fechaSuceso; }
    public Instant getFechaRegistro() { return fechaRegistro; }
}
