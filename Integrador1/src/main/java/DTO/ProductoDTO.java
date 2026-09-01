package DTO;




public class ProductoDTO {
    private int idProducto;
    private String nombre;
    private float valor;
    private float recaudacion;


    @Override
    public String toString() {
        return String.format(
                "Producto #%-3d | %-20s | Precio: $%,.2f | Recaudación: $%,.2f",
                idProducto, nombre, valor, recaudacion
        );
    }

    public int getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(int idProducto) {
        this.idProducto = idProducto;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public float getRecaudacion() {
        return recaudacion;
    }

    public void setRecaudacion(float recaudacion) {
        this.recaudacion = recaudacion;
    }

    public float getValor() {
        return valor;
    }

    public void setValor(float valor) {
        this.valor = valor;
    }


    // Constructor secundario (sin ID) por si tu consulta SQL solo devuelve el nombre
    public ProductoDTO(String nombre, float valor, float recaudacion) {
        this.nombre = nombre;
        this.valor = valor;
        this.recaudacion = recaudacion;
    }
}