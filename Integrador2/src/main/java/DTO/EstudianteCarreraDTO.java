package DTO;

public class EstudianteCarreraDTO {

    private int id;
    private int dniEstudiante;
    private int idCarrera;
    private int inscripcion;
    private int graduacion;
    private int antiguedad;

    public EstudianteCarreraDTO(int id, int dniEstudiante, int idCarrera,
                                int inscripcion, int graduacion, int antiguedad) {
        this.id = id;
        this.dniEstudiante = dniEstudiante;
        this.idCarrera = idCarrera;
        this.inscripcion = inscripcion;
        this.graduacion = graduacion;
        this.antiguedad = antiguedad;
    }

    // Getters
    public int getId() {
        return id;
    }

    public int getDniEstudiante() {
        return dniEstudiante;
    }

    public int getIdCarrera() {
        return idCarrera;
    }

    public int getInscripcion() {
        return inscripcion;
    }

    public int getGraduacion() {
        return graduacion;
    }

    public int getAntiguedad() {
        return antiguedad;
    }

    // Setters
    public void setId(int id) {
        this.id = id;
    }

    public void setDniEstudiante(int dniEstudiante) {
        this.dniEstudiante = dniEstudiante;
    }

    public void setIdCarrera(int idCarrera) {
        this.idCarrera = idCarrera;
    }

    public void setInscripcion(int inscripcion) {
        this.inscripcion = inscripcion;
    }

    public void setGraduacion(int graduacion) {
        this.graduacion = graduacion;
    }

    public void setAntiguedad(int antiguedad) {
        this.antiguedad = antiguedad;
    }
}