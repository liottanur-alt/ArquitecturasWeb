package Repository.mysql;

import java.sql.Connection;

import DAO.ProductoDAO;
import DTO.ProductoDTO;
import Entities.Producto;
import org.apache.commons.csv.*;


import java.io.FileReader;
import java.sql.*;
import java.util.ArrayList;

public class MySQLProductoDAO implements ProductoDAO {
    private Connection conn;

    public MySQLProductoDAO(Connection conn) {
        this.conn = conn;
        crearTablaSiNoExiste();
    }

    private void crearTablaSiNoExiste() {
        String sql = "CREATE TABLE IF NOT EXISTS producto (" +
                "idProducto INT PRIMARY KEY AUTO_INCREMENT, " +
                "nombre VARCHAR(100) NOT NULL, " +
                "valor FLOAT NOT NULL" +
                ")";

        try (Statement sentencia = conn.createStatement()) {
            sentencia.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Error creando tabla facturas", e);
        }
    }

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
                if (ps != null)
                    ps.close();
                conn.commit();
            } catch (SQLException ex) {
                System.err.println("Error al cerrar la conexion");
            }
        }
        return productos;
    }

    public void insertarDatosCsv() {
        try {
            ArrayList<Producto> productos = new ArrayList<Producto>();
            CSVParser parser = CSVFormat.DEFAULT.withHeader().parse(new FileReader("src/main/resources/productos.csv"));
            for (CSVRecord row : parser) {
                productos.add(new Producto(Integer.parseInt(row.get("idProducto")), (row.get("nombre")), Float.parseFloat(row.get("valor"))));
            }
            this.insertarDatos(productos);

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void insertarDatos(ArrayList<Producto> productos) {
        String sql = "INSERT INTO Producto (idProducto,nombre,valor) VALUES (?,?,?)";
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(sql);
            for (Producto p : productos) {
                ps.setInt(1, p.getId());
                ps.setString(2, p.getNombre());
                ps.setFloat(3, p.getValor());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            System.out.println("Datos Cargados con Exito! Producto");
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

    public ProductoDTO getRecaudacion() {
        String sql = " SELECT p.nombre, p.valor, SUM(p.valor * fp.cantidad) as recaudacion FROM Producto p JOIN Factura_Producto fp ON p.idProducto = fp.idProducto  GROUP BY p.idProducto ORDER BY recaudacion DESC LIMIT 1 ";

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
                ps.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return productoDTO;
    }
}