package entities;

import jakarta.persistence.*;

@Entity
@Table(name = "estudiante")
public class Estudiante {
    //DNI,nombre,apellido,edad,genero,ciudad,LU
    @Id
    @Column(name = "dni")
    private int DNI;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "apellido", nullable = false)
    private String apellido;

    @Column(name = "edad", nullable = false)
    private int edad;

    @Column(name = "genero", nullable = false)
    private String genero;

    @Column(name = "ciudad", nullable = false)
    private String ciudad;

    @Column(name = "lu", nullable = false)
    private int LU;

    public Estudiante(int DNI, String nombre, String apellido, int edad,  String genero, String ciudad, int LU) {
        this.DNI = DNI;
        this.nombre = nombre;
        this.apellido = apellido;
        this.edad = edad;
        this.genero = genero;
        this.ciudad = ciudad;
        this.LU = LU;
    }

    public Estudiante() {}

    public int getDNI() {
        return DNI;
    }

    public void setDNI(int DNI) {
        this.DNI = DNI;
    }

    public String getEstudianteNombre() {
        return nombre;
    }

    public void setEstudianteNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEstudianteApellido() {
        return apellido;
    }

    public void setEstudianteApellido(String apellido) {
        this.apellido = apellido;
    }

    public int getEdad() {
        return edad;
    }

    public void setEdad(int edad) {
        this.edad = edad;
    }

    public String getEstudianteGenero() {
        return genero;
    }

    public void setEstudianteGenero(String genero) {
        this.genero = genero;
    }

    public String getEstudianteCiudad() {
        return ciudad;
    }

    public void setEstudianteCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public int getLU() {
        return LU;
    }

    public void setLU(int LU) {
        this.LU = LU;
    }

    @Override
    public String toString() {
        return "Estudiante{" + "DNI" + DNI + ", nombre='" + nombre + '\'' + ", apellido='" + apellido + '\'' +
                ", edad='" + edad + '\'' + ", genero='" + genero + '\'' + ", ciudad='" + ciudad + '\'' + ", LU=" + LU + '}';
    }
}