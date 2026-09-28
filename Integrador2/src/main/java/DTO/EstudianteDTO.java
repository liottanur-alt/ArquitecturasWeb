package DTO;

import java.time.LocalDate;

public class EstudianteDTO {

    private int dni;
    private String nombre;
    private String apellido;
    private int edad;
    private String genero;
    private String ciudadResidencia;
    private int lu;
    private LocalDate fechaNacimiento;

    // Constructor para consultas de proyección JPQL "SELECT new DTO.EstudianteDTO(...)"
    public EstudianteDTO(int dni, String nombre, String apellido, int edad, String genero, String ciudadResidencia, int lu, LocalDate fechaNacimiento) {
        this.dni = dni;
        this.nombre = nombre;
        this.apellido = apellido;
        this.edad = edad;
        this.genero = genero;
        this.ciudadResidencia = ciudadResidencia;
        this.lu = lu;
        this.fechaNacimiento = fechaNacimiento;
    }

    // Getters
    public int getDni() { return dni; }
    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public int getEdad() { return edad; }
    public String getGenero() { return genero; }
    public String getCiudadResidencia() { return ciudadResidencia; }
    public int getLu() { return lu; }
    public LocalDate getFechaNacimiento() { return fechaNacimiento; }

    @Override
    public String toString() {
        return "EstudianteDTO{" +
                "LU=" + lu +
                ", DNI=" + dni +
                ", Nombre='" + nombre + " " + apellido + '\'' +
                ", Edad=" + edad +
                ", Género='" + genero + '\'' +
                ", Ciudad='" + ciudadResidencia + '\'' +
                '}';
    }
}