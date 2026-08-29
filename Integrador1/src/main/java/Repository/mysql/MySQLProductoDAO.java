package Repository.mysql;

import DAO.ProductoDAO;
import entities.Producto;

import java.sql.*;
import java.util.ArrayList;

public class MySQLProductoDAO implements ProductoDAO {

    private final Connection cn;

    public MySQLProductoDAO(Connection cn) {
        this.cn = cn;
        crearTablaSiNoExiste();
    }

    private void crearTablaSiNoExiste() {
        final String sql = "CREATE TABLE IF NOT EXISTS productos (" +
                "id INT PRIMARY KEY AUTO_INCREMENT," +
                "nombre VARCHAR(120) NOT NULL," +
                "valor FLOAT" +
                ")";

        try (Statement st = cn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Error creando tabla 'productos'", e);
        }
    }

    @Override
    public Producto buscarPorId(int id) {
        final String sql = "SELECT id, nombre, valor FROM productos WHERE id=?";

        try (PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error en buscarPorId", e);
        }
    }

    @Override
    public ArrayList<Producto> buscarTodos() {
        final String sql = "SELECT id, nombre, valor FROM productos";
        ArrayList<Producto> productos = new ArrayList<>();

        try (PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                productos.add(map(rs));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error en buscarTodos", e);
        }

        return productos;
    }

    @Override
    public void insertar(Producto producto) {
        final String sql = "INSERT INTO productos (nombre, valor) VALUES (?,?)";

        try (PreparedStatement ps = cn.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, producto.getNombre());
            ps.setFloat(2, producto.getValor());

            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    producto.setId(keys.getInt(1));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error en insertar(producto)", e);
        }
    }

    @Override
    public void actualizar(Producto producto) {
        final String sql = "UPDATE productos SET nombre=?, valor=? WHERE id=?";

        try (PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setString(1, producto.getNombre());
            ps.setFloat(2, producto.getValor());
            ps.setInt(3, producto.getId());

            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error en actualizar(producto)", e);
        }
    }

    @Override
    public void borrar(int id) {
        final String sql = "DELETE FROM productos WHERE id=?";

        try (PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error en borrar(producto)", e);
        }
    }

    @Override
    public void borrarTodo() {
        try (Statement st = cn.createStatement()) {

            st.executeUpdate("DELETE FROM productos");
            st.execute("ALTER TABLE productos AUTO_INCREMENT = 1");

        } catch (SQLException e) {
            throw new RuntimeException("Error borrando 'productos'", e);
        }
    }

    @Override
    public void insertarDatosCsv() {
        // Se implementará cuando corresponda cargar los datos desde CSV.
    }

    @Override
    public Producto buscarPorRecaudacion() {
        // Se implementará según lo que pida el TP.
        return null;
    }

    private Producto map(ResultSet rs) throws SQLException {
        Producto p = new Producto();

        p.setId(rs.getInt("id"));
        p.setNombre(rs.getString("nombre"));
        p.setValor(rs.getFloat("valor"));

        return p;
    }
}