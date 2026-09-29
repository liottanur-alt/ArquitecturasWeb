package DTO;

public class CarreraDTO {

    private String nombreCarrera;
    private int anio;
    private long inscriptos;
    private long egresados;

    public CarreraDTO(String nombreCarrera, int anio) {
        this.nombreCarrera = nombreCarrera;
        this.anio = anio;
        this.inscriptos = 0;
        this.egresados = 0;
    }

    public CarreraDTO(String nombreCarrera, int anio, long inscriptos, long egresados) {
        this.nombreCarrera = nombreCarrera;
        this.anio = anio;
        this.inscriptos = inscriptos;
        this.egresados = egresados;
    }

    // Getters y Setters
    public String getNombreCarrera() { return nombreCarrera; }
    public void setNombreCarrera(String nombreCarrera) { this.nombreCarrera = nombreCarrera; }

    public int getAnio() { return anio; }
    public void setAnio(int anio) { this.anio = anio; }

    public long getInscriptos() { return inscriptos; }
    public void setInscriptos(long inscriptos) { this.inscriptos = inscriptos; }

    public long getEgresados() { return egresados; }
    public void setEgresados(long egresados) { this.egresados = egresados; }

    @Override
    public String toString() {
        return String.format("Carrera: %-25s | Año: %d | Inscriptos: %-5d | Egresados: %-5d",
                nombreCarrera, anio, inscriptos, egresados);
    }
}