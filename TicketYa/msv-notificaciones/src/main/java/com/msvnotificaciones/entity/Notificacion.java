package com.msvnotificaciones.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notificaciones")
public class Notificacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "suceso_id", nullable = false, unique = true)
    private Long sucesoId;
    @Column(name = "cliente_id")
    private Long idCliente;
    @Column(length = 320)
    private String destinatario;
    @Column(nullable = false, length = 120)
    private String asunto;
    @Column(nullable = false, length = 1000)
    private String mensaje;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EstadoNotificacion estado;
    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    protected Notificacion() {}

    public Notificacion(Long sucesoId, Long idCliente, String destinatario, String asunto,
                        String mensaje, EstadoNotificacion estado) {
        this.sucesoId = sucesoId;
        this.idCliente = idCliente;
        this.destinatario = destinatario;
        this.asunto = asunto;
        this.mensaje = mensaje;
        this.estado = estado;
        this.fechaCreacion = Instant.now();
    }

    public Long getId() { return id; }
    public Long getSucesoId() { return sucesoId; }
    public Long getIdCliente() { return idCliente; }
    public String getDestinatario() { return destinatario; }
    public String getAsunto() { return asunto; }
    public String getMensaje() { return mensaje; }
    public EstadoNotificacion getEstado() { return estado; }
    public void setEstado(EstadoNotificacion estado) { this.estado = estado; }
    public Instant getFechaCreacion() { return fechaCreacion; }
}
