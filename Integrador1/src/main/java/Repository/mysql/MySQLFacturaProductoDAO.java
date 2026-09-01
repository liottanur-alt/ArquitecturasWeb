package Repository.mysql;

import DAO.FacturaProductoDAO;
import Entities.FacturaProducto;
import org.apache.commons.csv.*;


import java.io.FileReader;
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
    public void insertarDatos(ArrayList<FacturaProducto> fps) {
        String sql = "INSERT INTO Factura_Producto (idFactura, idProducto, cantidad) VALUES (?,?,?)";
        PreparedStatement ps = null;
        try {
            conn.setAutoCommit(false); // <--- REQUERIDO PARA JDBC
            ps = conn.prepareStatement(sql);

            for (FacturaProducto fp : fps) {
                ps.setInt(1, fp.getIdFactura());
                ps.setInt(2, fp.getIdProducto());
                ps.setInt(3, fp.getCantidad());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            System.out.println("Datos de FacturaProducto cargados con exito!");
        } catch (Exception e) {
            System.out.println("Error insertando FacturaProducto: " + e.getMessage());
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } finally {
            try {
                if (ps != null) ps.close();
                conn.setAutoCommit(true); // <--- RESTAURAR AUTOCOMMIT
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }

    @Override
    public void insertarDatosCsv() {
        try {
            ArrayList<FacturaProducto> fp = new ArrayList<FacturaProducto>();
            CSVParser parser = CSVFormat.DEFAULT.withHeader().parse(new  FileReader("Integrador1/src/main/Resources/facturas-productos.csv"));
            for (CSVRecord row : parser) {
                int idFactura = Integer.parseInt(row.get("idFactura"));
                int idProducto = Integer.parseInt(row.get("idProducto"));
                int cantidad = Integer.parseInt(row.get("cantidad"));
                fp.add(new FacturaProducto(idFactura, idProducto, cantidad));
            }
            this.insertarDatos(fp);

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    @Override
    public ArrayList<FacturaProducto> getFacturasProductos() {

        String sql = "SELECT idFactura, idProducto, cantidad " +
                "FROM Factura_Producto";

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