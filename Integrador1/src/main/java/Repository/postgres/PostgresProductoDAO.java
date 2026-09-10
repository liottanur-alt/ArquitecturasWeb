package Repository.postgres;

import java.sql.Connection;
import DAO.ProductoDAO;
import DTO.ProductoDTO;
import Entities.Producto;
import org.apache.commons.csv.*;

import java.io.FileReader;
import java.sql.*;
import java.util.ArrayList;

public class PostgresProductoDAO implements ProductoDAO {

    private static PostgresProductoDAO instance;
    private Connection conn;

    private PostgresProductoDAO(Connection conn) {
        this.conn = conn;
    }

    public static synchronized PostgresProductoDAO getInstance(Connection conn) {
        if (instance == null) {
            instance = new PostgresProductoDAO(conn);
        }
        return instance;
    }

    @Override
    public ArrayList<Producto> getProductos() {
        ArrayList<Producto> productos = new ArrayList<>();
        String select = "SELECT * FROM Producto";
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = conn.prepareStatement(select);
            rs = ps.executeQuery();
            while (rs.next()) {
                productos.add(new Producto(rs.getInt("idProducto"), rs.getString("nombre"), rs.getFloat("valor")));
            }
        } catch (Exception e) {
            System.err.println("Error al consultar productos");
        } finally {
            try {
                if (ps != null) ps.close();
            } catch (SQLException ex) {
                System.err.println("Error al cerrar PreparedStatement");
            }
        }
        return productos;
    }

    @Override
    public void insertarDatosCsv() {
        try {
            ArrayList<Producto> productos = new ArrayList<>();
            CSVParser parser = CSVFormat.DEFAULT.withHeader().parse(new FileReader("Integrador1/src/main/Resources/productos.csv"));
            for (CSVRecord row : parser) {
                productos.add(new Producto(Integer.parseInt(row.get("idProducto")), (row.get("nombre")), Float.parseFloat(row.get("valor"))));
            }
            this.insertarDatos(productos);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void insertarDatos(ArrayList<Producto> productos) {
        String sql = "INSERT INTO Producto (idProducto, nombre, valor) VALUES (?,?,?)";
        PreparedStatement ps = null;
        try {
            conn.setAutoCommit(false);
            ps = conn.prepareStatement(sql);
            for (Producto p : productos) {
                ps.setInt(1, p.getId());
                ps.setString(2, p.getNombre());
                ps.setFloat(3, p.getValor());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            System.out.println("Datos Cargados con Exito en Postgres! Producto");
        } catch (Exception e) {
            System.out.println("Error insertando Productos: " + e.getMessage());
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        } finally {
            try {
                if (ps != null) ps.close();
                if (conn != null) conn.setAutoCommit(true);
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }

    @Override
    public ProductoDTO getRecaudacion() {
        String sql = "SELECT p.nombre, p.valor, SUM(p.valor * fp.cantidad) as recaudacion FROM Producto p JOIN Factura_Producto fp ON p.idProducto = fp.idProducto GROUP BY p.idProducto, p.nombre, p.valor ORDER BY recaudacion DESC LIMIT 1";
        
        PreparedStatement ps = null;
        ResultSet rs = null;
        ProductoDTO productoDTO = null;
        try {
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            if (rs.next()) {
                productoDTO = new ProductoDTO(rs.getString("nombre"), rs.getFloat("valor"), rs.getFloat("recaudacion"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (ps != null) ps.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return productoDTO;
    }
}