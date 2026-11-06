package msvevento.entity;


import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "eventos")
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String descripcion;

    @Enumerated(EnumType.STRING)
    private TipoEvento tipo;

    private Long idOrganizador;

    @ManyToMany
    @JoinTable(
            name = "evento_artista",
            joinColumns = @JoinColumn(name = "id_evento"),
            inverseJoinColumns = @JoinColumn(name = "id_artista")
    )
    private List<Artista> participantes = new ArrayList<>();

    private LocalDateTime fechaHora;

    @ManyToOne
    @JoinColumn(name = "id_lugar")
    private Lugar lugar;

    @Enumerated(EnumType.STRING)
    private EstadoEvento estado;

    @Enumerated(EnumType.STRING)
    private EstadoPublicacion estadoPublicacion;

    private boolean demanda;

    public Evento() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public TipoEvento getTipo() {
        return tipo;
    }

    public void setTipo(TipoEvento tipo) {
        this.tipo = tipo;
    }

    public Long getIdOrganizador() {
        return idOrganizador;
    }

    public void setIdOrganizador(Long idOrganizador) {
        this.idOrganizador = idOrganizador;
    }

    public List<Artista> getParticipantes() {
        return participantes;
    }

    public void setParticipantes(List<Artista> participantes) {
        this.participantes = participantes;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public void setFechaHora(LocalDateTime fechaHora) {
        this.fechaHora = fechaHora;
    }

    public Lugar getLugar() {
        return lugar;
    }

    public void setLugar(Lugar lugar) {
        this.lugar = lugar;
    }

    public EstadoEvento getEstado() {
        return estado;
    }

    public void setEstado(EstadoEvento estado) {
        this.estado = estado;
    }

    public EstadoPublicacion getEstadoPublicacion() {
        return estadoPublicacion;
    }

    public void setEstadoPublicacion(EstadoPublicacion estadoPublicacion) {
        this.estadoPublicacion = estadoPublicacion;
    }

    public boolean isDemanda() {
        return demanda;
    }

    public void setDemanda(boolean demanda) {
        this.demanda = demanda;
    }
}
