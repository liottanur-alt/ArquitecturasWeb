package entities;

public class Carreras {
    //id_carrera, carrera, duracion
    private int idCarrera;
    private String nombreCarrera;
    private int duracion;

    public Carreras(int idCarrera, String nombreCarrera, int duracion) {
        this.idCarrera = idCarrera;
        this.nombreCarrera = nombreCarrera;
        this.duracion = duracion;
    }

    public Carreras() {}

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
