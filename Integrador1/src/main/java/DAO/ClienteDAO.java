package DAO;

import Entities.Cliente;
import DTO.ClienteDTO;
import java.util.ArrayList;

public interface ClienteDAO {
    Cliente buscarPorId(int id);
    ArrayList<Cliente> buscarTodo();
    void crearCliente(Cliente u);
    void actualizar(Cliente u);
    void borrar(Long id);
    void borrarTodo();
    void insertarDatosCsv();
    ArrayList<ClienteDTO> getClientesByMayorFacturacion();
}