package com.ticketya.eventos.entities;

import com.ticketya.eventos.enums.TipoSector;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sectores")
public class Sector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_lugar")
    private Lugar lugar;

    @Enumerated(EnumType.STRING)
    private TipoSector tipo;

    private Integer espaciosDisponibles;
    private Double precioAsiento;

    @OneToMany(mappedBy = "sector", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Asiento> asientos = new ArrayList<>();

    public Sector() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Lugar getLugar() {
        return lugar;
    }

    public void setLugar(Lugar lugar) {
        this.lugar = lugar;
    }

    public TipoSector getTipo() {
        return tipo;
    }

    public void setTipo(TipoSector tipo) {
        this.tipo = tipo;
    }

    public Integer getEspaciosDisponibles() {
        return espaciosDisponibles;
    }

    public void setEspaciosDisponibles(Integer espaciosDisponibles) {
        this.espaciosDisponibles = espaciosDisponibles;
    }

    public Double getPrecioAsiento() {
        return precioAsiento;
    }

    public void setPrecioAsiento(Double precioAsiento) {
        this.precioAsiento = precioAsiento;
    }

    public List<Asiento> getAsientos() {
        return asientos;
    }

    public void setAsientos(List<Asiento> asientos) {
        this.asientos = asientos;
    }
}