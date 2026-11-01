package Entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.Period;

@Entity
@Table(name = "estudiante") // Nombre explicito de la tabla
public class Estudiante {

    @Id
    @Column(name = "dni")
    private Long dni;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "apellido", nullable = false)
    private String apellido;

    @Column(name = "genero", nullable = false)
    private String genero;

    @Column(name = "ciudad", nullable = false)
    private String ciudad;

    @Column(name = "lu", nullable = false, unique = true)
    private int lu;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    public Estudiante() {
    }

    public Estudiante(Long dni, String nombre, String apellido, int edadCsv, String genero, String ciudad, int lu) {
        this.dni = dni;
        this.nombre = nombre;
        this.apellido = apellido;
        this.genero = genero;
        this.ciudad = ciudad;
        this.lu = lu;
        int anioNacimiento = LocalDate.now().getYear() - edadCsv;
        this.fechaNacimiento = LocalDate.of(anioNacimiento, 1, 1);
    }

    public Estudiante(Long dni, String nombre, String apellido, String genero, String ciudad, int lu, LocalDate fechaNacimiento) {
        this.dni = dni;
        this.nombre = nombre;
        this.apellido = apellido;
        this.genero = genero;
        this.ciudad = ciudad;
        this.lu = lu;
        this.fechaNacimiento = fechaNacimiento;
    }

    // Getters y Setters
    public Long getDni() { return dni; }
    public void setDni(Long dni) { this.dni = dni; }

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

    @Transient
    public int getEdad() {
        if (this.fechaNacimiento == null) return 0;
        return Period.between(this.fechaNacimiento, LocalDate.now()).getYears();
    }
}