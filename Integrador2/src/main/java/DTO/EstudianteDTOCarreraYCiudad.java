package DTO;

import java.time.LocalDate;

public class EstudianteDTOCarreraYCiudad {

    private long dni;
    private String nombre;
    private String apellido;
    private String genero;
    private String ciudad;
    private int lu;
    private LocalDate fechaNacimiento;

    public EstudianteDTOCarreraYCiudad(long dni, String nombre, String apellido, String genero, String ciudad,
                                       int lu, LocalDate fechaNacimiento) {
        this.dni = dni;
        this.nombre = nombre;
        this.apellido = apellido;
        this.genero = genero;
        this.ciudad = ciudad;
        this.lu = lu;
        this.fechaNacimiento = fechaNacimiento;
    }

    // Getters y Setters
    public long getDni() { return dni; }
    public void setDni(long dni) { this.dni = dni; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }

    public String getGenero() { return genero; }
    public void setGenero(String genero) { this.genero = genero; }

    public String getCiudad() { return ciudad; }
    public void setCiudad(String ciudad) { this.ciudad = ciudad; }

    public int getLu() { return lu; }
    public void setLu(int lu) { this.lu = lu; }

    public LocalDate getFechaNacimiento() { return fechaNacimiento; }
    public void setFechaNacimiento(LocalDate fechaNacimiento) { this.fechaNacimiento = fechaNacimiento; }
}