import java.util.List;
public interface Producto {
    Cliente buscarPorId(Long id);
    List<Cliente> buscarTodos();
    void crear(Cliente u);
    void actualizar(Cliente u);
    void borrar (Cliente u);
    void borrarTodo (Long id);
}