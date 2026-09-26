package entities;

import jakarta.persistence.*;

@Entity
@Table(name = "estudianteCarrera")
public class EstudianteCarrera {
//id,id_estudiante,id_carrera,inscripcion,graduacion,antiguedad
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "id_estudiante", nullable = false)
    private Estudiante estudiante;

    @ManyToOne
    @JoinColumn(name = "id_carrera", nullable = false)
    private Carrera carrera;

    @Column(name = "inscripcion", nullable = false)
    private int inscripcion;

    @Column(name = "graduacion")
    private int gradaucion;

    @Column(name = "antiguedad", nullable = false)
    private int antiguedad;

    public EstudianteCarrera(int id, int id_estudiante, int id_carrera, int inscripcion, int graducion, int antiguedad) {
        this.id = id;
        this.id_estudiante = id_estudiante;
        this.id_carrera = id_carrera;
        this.inscripcion = inscripcion;
        this.gradaucion = graducion;
        this.antiguedad = antiguedad;
    }

    public EstudianteCarrera() {}

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId_estudiante() {
        return id_estudiante;
    }

    public void setId_estudiante(int id_estudiante) {
        this.id_estudiante = id_estudiante;
    }

    public int getId_carrera() {
        return id_carrera;
    }

    public void setId_carrera(int id_carrera) {
        this.id_carrera = id_carrera;
    }

    public int getInscripcion() {
        return inscripcion;
    }

    public void setInscripcion(int inscripcion) {
        this.inscripcion = inscripcion;
    }

    public int getGraducion() {
        return graducion;
    }

    public void setGraducion(int graducion) {
        this.graducion = graducion;
    }

    public int getAntiguedad() {
        return antiguedad;
    }

    public void setAntiguedad(int antiguedad) {
        this.antiguedad = antiguedad;
    }

    @Override
    public String toString() {
        return "EstudianteCarrera{" + "id" + id + ", id Estudiante='" +
                id_estudiante + '\'' + ", id carrera=" + id_carrera + ", inscripcion=" + inscripcion +
                ", graduacion=" + graducion + ", antiguedad=" + antiguedad + '}';
    }
}
