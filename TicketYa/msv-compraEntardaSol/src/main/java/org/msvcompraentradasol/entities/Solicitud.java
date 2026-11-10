package org.msvcompraentradasol.entities;

import java.time.Instant;
import jakarta.persistence.*;
import org.msvcompraentradasol.enums.EstadoReintegro;
import org.msvcompraentradasol.enums.EstadoSolicitud;

@Entity
@Table(name = "solicitud")
public class Solicitud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_entrada", nullable = false)
    private Entrada entrada;

    @Column(name = "id_cliente", nullable = false)
    private Long idCliente;

    // Se asigna cuando un administrador resuelve la solicitud.
    @Column(name = "id_administrador")
    private Long idAdministrador;

    @Column(name = "fecha_solicitud", nullable = false)
    private Instant fechaSolicitud;

    @Column(name = "fecha_resolucion")
    private Instant fechaResolucion;

    @Column(name = "referencia_reintegro")
    private String referenciaReintegro;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_reintegro")
    private EstadoReintegro estadoReintegro;

    @Column(nullable = false)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_solicitud", nullable = false)
    private EstadoSolicitud estadoSolicitud;

    public Long getId() {
        return id;
    }

    public Entrada getEntrada() {
        return entrada;
    }

    public void setEntrada(Entrada entrada) {
        this.entrada = entrada;
    }

    public Long getIdCliente() {
        return idCliente;
    }

    public void setIdCliente(Long idCliente) {
        this.idCliente = idCliente;
    }

    public Long getIdAdministrador() {
        return idAdministrador;
    }

    public void setIdAdministrador(Long idAdministrador) {
        this.idAdministrador = idAdministrador;
    }

    public Instant getFechaSolicitud() {
        return fechaSolicitud;
    }

    public void setFechaSolicitud(Instant fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }

    public Instant getFechaResolucion() {
        return fechaResolucion;
    }

    public void setFechaResolucion(Instant fechaResolucion) {
        this.fechaResolucion = fechaResolucion;
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

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public EstadoSolicitud getEstadoSolicitud() {
        return estadoSolicitud;
    }

    public void setEstadoSolicitud(EstadoSolicitud estadoSolicitud) {
        this.estadoSolicitud = estadoSolicitud;
    }
}
