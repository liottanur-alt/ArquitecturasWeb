package DAO;
import entities.Cliente;
import java.util.List;

public interface ClienteDAO {
    Cliente buscarPorId(int id);
    List<Cliente> buscarTodo();
    void crearCliente(Cliente u);
    void actualizar(Cliente u);
    void borrar(Long id);
    void borrarTodo();
}
