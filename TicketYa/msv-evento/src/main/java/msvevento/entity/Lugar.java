package msvevento.entity;



import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "lugares", indexes = @jakarta.persistence.Index(name = "idx_lugar_ciudad_normalizada", columnList = "ciudad_normalizada"))
public class Lugar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String direccion;
    private Double latitud;
    private Double longitud;
    private String ciudad;

    @jakarta.persistence.Column(name = "ciudad_normalizada")
    private String ciudadNormalizada;

    public String getCiudadNormalizada() { return ciudadNormalizada; }

    @jakarta.persistence.PrePersist
    @jakarta.persistence.PreUpdate
    public void normalizarCiudad() { ciudadNormalizada = msvevento.service.CiudadNormalizada.de(ciudad); }

    @Enumerated(EnumType.STRING)
    private TipoLugar tipo;

    public Lugar() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public Double getLatitud() {
        return latitud;
    }

    public void setLatitud(Double latitud) {
        this.latitud = latitud;
    }

    public Double getLongitud() {
        return longitud;
    }

    public void setLongitud(Double longitud) {
        this.longitud = longitud;
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public TipoLugar getTipo() {
        return tipo;
    }

    public void setTipo(TipoLugar tipo) {
        this.tipo = tipo;
    }
}
