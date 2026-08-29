import java.sql.Array;
import java.util.ArrayList;
import java.util.List;

public interface ProductoDAO {
    ProductoDto buscarPorId(Long id);

    ArrayList<Producto> buscarTodos();

    void insertar(ArrayList<Productos> productos);

    void actualizar(Cliente u);

    void insertarDatosCsv();

    void borrar(Cliente u);

    void borrarTodo(Long id);

    ProductoDTO buscarPorRecaudacion();
}