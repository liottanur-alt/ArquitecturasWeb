package main.java.DTO;

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

    public String getNombreCarrera() {
        return nombreCarrera;
    }

    public void setNombreCarrera(String nombreCarrera) {
        this.nombreCarrera = nombreCarrera;
    }

    public int getAnio() {
        return anio;
    }

    public void setAnio(int anio) {
        this.anio = anio;
    }

    public Long getInscriptos() {
        return inscriptos;
    }

    public void setInscriptos(Long inscriptos) {
        this.inscriptos = inscriptos;
    }

    public Long getEgresados() {
        return egresados;
    }

    public void setEgresados(Long egresados) {
        this.egresados = egresados;
    }
}