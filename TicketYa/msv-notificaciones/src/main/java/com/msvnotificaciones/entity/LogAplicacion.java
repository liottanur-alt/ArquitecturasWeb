package com.msvnotificaciones.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "logs_aplicacion", indexes = {
    @Index(name = "idx_log_servicio_fecha", columnList = "servicio,fecha")
})
public class LogAplicacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String servicio;
    @Column(nullable = false, length = 15)
    private String nivel;
    @Column(nullable = false, length = 2000)
    private String mensaje;
    @Column(name = "correlation_id", length = 100)
    private String idCorrelacion;
    @Column(nullable = false)
    private Instant fecha;

    protected LogAplicacion() {}
    public LogAplicacion(String servicio, String nivel, String mensaje, String idCorrelacion, Instant fecha) {
        this.servicio = servicio;
        this.nivel = nivel;
        this.mensaje = mensaje;
        this.idCorrelacion = idCorrelacion;
        this.fecha = fecha;
    }
    public Long getId() { return id; }
    public String getServicio() { return servicio; }
    public String getNivel() { return nivel; }
    public String getMensaje() { return mensaje; }
    public String getCorrelationId() { return idCorrelacion; }
    public Instant getFecha() { return fecha; }
}
