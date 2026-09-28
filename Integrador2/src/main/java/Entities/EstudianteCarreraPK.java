package Entities;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class EstudianteCarreraPK implements Serializable {

    private long idEstudiante;
    private int idCarrera;

    public EstudianteCarreraPK() {
    }

    public EstudianteCarreraPK(long idEstudiante, int idCarrera) {
        this.idEstudiante = idEstudiante;
        this.idCarrera = idCarrera;
    }

    public long getIdEstudiante() {
        return idEstudiante;
    }

    public void setIdEstudiante(long idEstudiante) {
        this.idEstudiante = idEstudiante;
    }

    public int getIdCarrera() {
        return idCarrera;
    }

    public void setIdCarrera(int idCarrera) {
        this.idCarrera = idCarrera;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EstudianteCarreraPK)) return false;

        EstudianteCarreraPK that = (EstudianteCarreraPK) o;

        return idEstudiante == that.idEstudiante &&
                idCarrera == that.idCarrera;
    }

    @Override
    public int hashCode() {
        return Objects.hash(idEstudiante, idCarrera);
    }
}