package DAO;
import entities.Producto;
import entities.Cliente;
import java.util.ArrayList;

public interface ProductoDAO {
    Producto buscarPorId(int id);

    ArrayList<Producto> buscarTodos();

    void insertar(Producto producto);

    void actualizar(Producto producto);

    void insertarDatosCsv();

    void borrar(int id);

    void borrarTodo();

    Producto buscarPorRecaudacion();
}