package Repository.mysql;

import DAO.FacturaProductoDAO;
import entities.FacturaProducto;

import java.sql.*;
import java.util.ArrayList;

public class MySQLFacturaProductoDAO implements FacturaProductoDAO {

    private final Connection conexion;

    public MySQLFacturaProductoDAO(Connection conexion) {
        this.conexion = conexion;
        crearTablaSiNoExiste();
    }

    private void crearTablaSiNoExiste() {
        String sql = "CREATE TABLE IF NOT EXISTS factura_producto (" +
                "idFactura INT NOT NULL, " +
                "idProducto INT NOT NULL, " +
                "cantidad INT NOT NULL, " +
                "PRIMARY KEY (idFactura, idProducto)" +
                ")";

        try (Statement sentencia = conexion.createStatement()) {
            sentencia.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "Error creando tabla factura_producto", e);
        }
    }

    @Override
    public void insertarDatos(ArrayList<FacturaProducto> facturasProductos) {

        String sql = "INSERT INTO factura_producto " +
                "(idFactura, idProducto, cantidad) VALUES (?, ?, ?)";

        try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {

            for (FacturaProducto facturaProducto : facturasProductos) {

                sentencia.setInt(1, facturaProducto.getIdFactura());
                sentencia.setInt(2, facturaProducto.getIdProducto());
                sentencia.setInt(3, facturaProducto.getCantidad());

                sentencia.addBatch();
            }

            sentencia.executeBatch();

        } catch (SQLException e) {
            throw new RuntimeException("Error en insertarDatos", e);
        }
    }

    @Override
    public void insertarDatosCsv() {
        // Lo implementamos cuando trabajemos con el CSV.
    }

    @Override
    public ArrayList<FacturaProducto> obtenerFacturasProductos() {

        String sql = "SELECT idFactura, idProducto, cantidad " +
                "FROM factura_producto";

        ArrayList<FacturaProducto> facturasProductos = new ArrayList<>();

        try (PreparedStatement sentencia = conexion.prepareStatement(sql);
             ResultSet resultado = sentencia.executeQuery()) {

            while (resultado.next()) {
                facturasProductos.add(mapear(resultado));
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Error al obtener facturas y productos", e);
        }

        return facturasProductos;
    }

    private FacturaProducto mapear(ResultSet resultado) throws SQLException {

        FacturaProducto facturaProducto = new FacturaProducto();

        facturaProducto.setIdFactura(
                resultado.getInt("idFactura"));

        facturaProducto.setIdProducto(
                resultado.getInt("idProducto"));

        facturaProducto.setCantidad(
                resultado.getInt("cantidad"));

        return facturaProducto;
    }
}