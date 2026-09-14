package entities;

import jakarta.persistence.*;


@Entity
@Table(name = "carrera")
public class Carrera {
    //id_carrera, carrera, duracion
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idCarrera")
    private int idCarrera;
    @Column(name = "nombreCarrera", nullable = false, length = 100)
    private String nombreCarrera;
    @Column(name = "duracion")
    private int duracion;

    public Carrera(int idCarrera, String nombreCarrera, int duracion) {
        this.idCarrera = idCarrera;
        this.nombreCarrera = nombreCarrera;
        this.duracion = duracion;
    }

    public Carrera() {}

    public int getIdCarrera() {
        return idCarrera;
    }

    public void setIdCarrera(int idCarrera) {
        this.idCarrera = idCarrera;
    }

    public String getNombreCarrera() {
        return nombreCarrera;
    }

    public void setNombreCarrera(String nombreCarrera) {
        this.nombreCarrera = nombreCarrera;
    }

    public int getDuracion() {
        return duracion;
    }

    public void setDuracion(int duracion) {
        this.duracion = duracion;
    }

    @Override
    public String toString() {
        return "Carrera{" + "idCarrera" + idCarrera + ", nombre='" +
                nombreCarrera + '\'' + ", duracion=" + duracion + '}';
    }
}
