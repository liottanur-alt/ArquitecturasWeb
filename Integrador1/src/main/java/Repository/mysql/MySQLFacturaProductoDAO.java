package Repository.mysql;

import DAO.FacturaProductoDAO;
import entities.FacturaProducto;


import java.sql.*;
import java.util.ArrayList;

public class MySQLFacturaProductoDAO implements FacturaProductoDAO {

    private static MySQLFacturaProductoDAO instance;
    private Connection conn;

    // 2. Constructor PRIVADO para bloquear instanciaciones externas
    private MySQLFacturaProductoDAO(Connection conn) {
        this.conn = conn;
    }

    // 3. Metodo estático global para obtener la instancia única
    public static synchronized MySQLFacturaProductoDAO getInstance(Connection conn) {
        if (instance == null) {
            instance = new MySQLFacturaProductoDAO(conn);
        }
        return instance;
    }




    @Override
    public void insertarDatos(ArrayList<FacturaProducto> facturasProductos) {

        String sql = "INSERT INTO factura_producto " +
                "(idFactura, idProducto, cantidad) VALUES (?, ?, ?)";

        try (PreparedStatement sentencia = conn.prepareStatement(sql)) {

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
    public ArrayList<FacturaProducto> getFacturasProductos() {
        return null;
    }

    @Override
    public ArrayList<FacturaProducto> obtenerFacturasProductos() {

        String sql = "SELECT idFactura, idProducto, cantidad " +
                "FROM factura_producto";

        ArrayList<FacturaProducto> facturasProductos = new ArrayList<>();

        try (PreparedStatement sentencia = conn.prepareStatement(sql);
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