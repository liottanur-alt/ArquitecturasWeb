package DTO;

public class CarreraDTO {
    private String nombreCarrera;
    private int anio;
    private Long inscriptos;
    private Long egresados;

    public CarreraDTO(String nombreCarrera, int anio, Long inscriptos, Long egresados) {
        this.nombreCarrera = nombreCarrera;
        this.anio = anio;
        this.inscriptos = inscriptos;
        this.egresados = egresados;
    }

    // Getters
    public String getNombreCarrera() {
        return nombreCarrera;
    }

    public int getAnio() {
        return anio;
    }

    public Long getInscriptos() {
        return inscriptos;
    }

    public Long getEgresados() {
        return egresados;
    }

    // Setters
    public void setNombreCarrera(String nombreCarrera) {
        this.nombreCarrera = nombreCarrera;
    }

    public void setAnio(int anio) {
        this.anio = anio;
    }

    public void setInscriptos(Long inscriptos) {
        this.inscriptos = inscriptos;
    }

    public void setEgresados(Long egresados) {
        this.egresados = egresados;
    }
}