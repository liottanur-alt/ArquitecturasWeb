package Repository.mysql;

import DAO.ClienteDAO;
import entities.Cliente;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MySQLClienteDAO implements ClienteDAO {

    private static MySQLClienteDAO instance;
    private Connection conn;

    // 2. Constructor PRIVADO para bloquear instanciaciones externas
    private MySQLClienteDAO(Connection conn) {
        this.conn = conn;
    }

    // 3. Metodo estático global para obtener la instancia única
    public static synchronized MySQLClienteDAO getInstance(Connection conn) {
        if (instance == null) {
            instance = new MySQLClienteDAO(conn);
        }
        return instance;
    }





    @Override
    public Cliente buscarPorId(int id) {
        String sql = "SELECT idCliente, nombre, email FROM clientes WHERE idCliente = ?";

        try (PreparedStatement sentencia = conn.prepareStatement(sql)) {

            sentencia.setInt(1, id);

            try (ResultSet resultado = sentencia.executeQuery()) {
                return resultado.next() ? mapear(resultado) : null;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error en buscarPorId", e);
        }
    }

    @Override
    public ArrayList<Cliente> buscarTodo() {
        String sql = "SELECT idCliente, nombre, email FROM clientes";

        ArrayList<Cliente> clientes = new ArrayList<>();

        try (PreparedStatement sentencia = conn.prepareStatement(sql);
             ResultSet resultado = sentencia.executeQuery()) {

            while (resultado.next()) {
                clientes.add(mapear(resultado));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error en buscarTodo", e);
        }

        return clientes;
    }

    @Override
    public void crearCliente(Cliente cliente) {
        String sql = "INSERT INTO clientes (nombre, email) VALUES (?, ?)";

        try (PreparedStatement sentencia =
                     conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            sentencia.setString(1, cliente.getNombre());
            sentencia.setString(2, cliente.getEmail());

            sentencia.executeUpdate();

            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                if (claves.next()) {
                    cliente.setIdCliente(claves.getInt(1));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error en crearCliente", e);
        }
    }

    @Override
    public void actualizar(Cliente cliente) {
        String sql = "UPDATE clientes SET nombre = ?, email = ? WHERE idCliente = ?";

        try (PreparedStatement sentencia = conn.prepareStatement(sql)) {

            sentencia.setString(1, cliente.getNombre());
            sentencia.setString(2, cliente.getEmail());
            sentencia.setInt(3, cliente.getIdCliente());

            sentencia.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error en actualizar", e);
        }
    }

    @Override
    public void borrar(Long id) {
        String sql = "DELETE FROM clientes WHERE idCliente = ?";

        try (PreparedStatement sentencia = conn.prepareStatement(sql)) {

            sentencia.setLong(1, id);
            sentencia.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error en borrar", e);
        }
    }

    @Override
    public void borrarTodo() {
        try (Statement sentencia = conn.createStatement()) {

            sentencia.executeUpdate("DELETE FROM clientes");
            sentencia.execute("ALTER TABLE clientes AUTO_INCREMENT = 1");

        } catch (SQLException e) {
            throw new RuntimeException("Error borrando clientes", e);
        }
    }

    @Override
    public void insertarDatosCsv(){
            try {
                ArrayList<Cliente> clientes = new ArrayList<Cliente>();
                CSVParser parser = CSVFormat.DEFAULT.withHeader().parse(new FileReader("src/main/resources/clientes.csv"));
                for (CSVRecord row : parser) {
                    clientes.add(new Cliente(Integer.parseInt(row.get("idCliente")), (row.get("nombre")), (row.get("email")) );
                }
                this.insertarDatos(clientes);

            } catch (Exception e) {
                System.out.println(e);
            }
        }

            public void insertarDatos(ArrayList<Cliente> cliente) {
            String sql = "INSERT INTO Cliente (idCliente, nombre, email) VALUES (?,?,?)";
            PreparedStatement ps = null;
            try {
                ps = conn.prepareStatement(sql);
                for (Cliente c : cliente) {
                    ps.setInt(1, c.getId());
                    ps.setString(2, c.getNombre());
                    ps.setString(3, c.getEmail());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
                System.out.println("Datos del Cliente cargados con exito!");
            } catch (Exception e) {
                System.out.println(e);
            } finally {
                try {
                    if (ps != null)
                        ps.close();
                } catch (Exception e) {
                    System.out.println(e);
                }

            }
        }

    // Convierte un registro de la BD en un objeto Cliente
    private Cliente mapear(ResultSet resultado) throws SQLException {
        Cliente cliente = new Cliente();

        cliente.setIdCliente(resultado.getInt("idCliente"));
        cliente.setNombre(resultado.getString("nombre"));
        cliente.setEmail(resultado.getString("email"));

        return cliente;
    }
}

