package Entities;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "estudiante")
public class Estudiante {

    @Id
    @Column(name = "dni")
    private int dni;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "apellido", nullable = false)
    private String apellido;

    @Column(name = "genero", nullable = false)
    private String genero;

    @Column(name = "ciudad", nullable = false)
    private String ciudad;

    @Column(name = "lu", nullable = false)
    private int lu;

    @Column(name = "anio_nacimiento", nullable = false)
    private int anioNacimiento;

    public Estudiante() {}

    // Constructor que recibe la edad desde el CSV y calcula el año de nacimiento
    public Estudiante(int dni, String nombre, String apellido, int edadCsv, String genero, String ciudad, int lu) {
        this.dni = dni;
        this.nombre = nombre;
        this.apellido = apellido;
        this.genero = genero;
        this.ciudad = ciudad;
        this.lu = lu;
        // Se calcula el año de nacimiento restando la edad al año actual
        this.anioNacimiento = LocalDate.now().getYear() - edadCsv;
    }

    // Getters y Setters
    public int getDni() {
        return dni;
    }

    public void setDni(int dni) {
        this.dni = dni;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public String getGenero() {
        return genero;
    }

    public void setGenero(String genero) {
        this.genero = genero;
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public int getLu() {
        return lu;
    }

    public void setLu(int lu) {
        this.lu = lu;
    }

    public int getAnioNacimiento() {
        return anioNacimiento;
    }

    public void setAnioNacimiento(int anioNacimiento) {
        this.anioNacimiento = anioNacimiento;
    }

    /**
     * @Transient indica a JPA que no persista este atributo como columna en la BD.
     * Retorna la edad calculada dinámicamente en base al año actual.
     */
    @Transient
    public int getEdad() {
        return LocalDate.now().getYear() - this.anioNacimiento;
    }
}