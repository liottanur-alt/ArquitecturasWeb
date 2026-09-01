package Repository.mysql;

import DAO.FacturaDAO;
import Entities.Factura;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.sql.*;
import java.util.ArrayList;

public class MySQLFacturaDAO implements FacturaDAO {

    private static MySQLFacturaDAO instance;
    private Connection conn;

    // 2. Constructor PRIVADO para bloquear instanciaciones externas
    private MySQLFacturaDAO(Connection conn) {
        this.conn = conn;
    }

    // 3. Metodo estático global para obtener la instancia única
    public static synchronized MySQLFacturaDAO getInstance(Connection conn) {
        if (instance == null) {
            instance = new MySQLFacturaDAO(conn);
        }
        return instance;
    }


    @Override
    public Factura buscarPorId(int id) {
        String sql = "SELECT idFactura, idCliente FROM Factura WHERE idFactura = ?";

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
    public ArrayList<Factura> buscarTodas() {
        String sql = "SELECT idFactura, idCliente FROM Factura";

        ArrayList<Factura> facturas = new ArrayList<>();

        try (PreparedStatement sentencia = conn.prepareStatement(sql);
             ResultSet resultado = sentencia.executeQuery()) {

            while (resultado.next()) {
                facturas.add(mapear(resultado));
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error en buscarTodas", e);
        }

        return facturas;
    }

    @Override
    public void insertar(Factura factura) {
        String sql = "INSERT INTO Factura (idCliente) VALUES (?)";

        try (PreparedStatement sentencia =
                     conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            sentencia.setInt(1, factura.getIdCliente());

            sentencia.executeUpdate();

            try (ResultSet claves = sentencia.getGeneratedKeys()) {
                if (claves.next()) {
                    factura.setIdFactura(claves.getInt(1));
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error en insertar", e);
        }
    }

    @Override
    public void actualizar(Factura factura) {
        String sql = "UPDATE Factura SET idCliente = ? WHERE idFactura = ?";

        try (PreparedStatement sentencia = conn.prepareStatement(sql)) {

            sentencia.setInt(1, factura.getIdCliente());
            sentencia.setInt(2, factura.getIdFactura());

            sentencia.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error en actualizar", e);
        }
    }

    @Override
    public void eliminar(Factura factura) {
        String sql = "DELETE FROM Factura WHERE idFactura = ?";

        try (PreparedStatement sentencia = conn.prepareStatement(sql)) {

            sentencia.setInt(1, factura.getIdFactura());

            sentencia.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error en eliminar", e);
        }
    }

    @Override
    public void insertarDatosCsv() {
        try {
            ArrayList<Factura> facturas = new ArrayList<Factura>();
            CSVParser parser = CSVFormat.DEFAULT.withHeader().parse(new FileReader("src/main/resources/facturas.csv"));
            for (CSVRecord row : parser) {
                int idFactura = Integer.parseInt(row.get("idFactura"));
                int idCliente = Integer.parseInt(row.get("idCliente"));
                facturas.add(new Factura(idFactura, idCliente));
            }
            this.insertarDatos(facturas);

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public void insertarDatos(ArrayList<Factura> fac) {
        String sql = "INSERT INTO Factura (idFactura,idCliente) VALUES (?,?)";
        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(sql);
            for (Factura f : fac) {
                ps.setInt(1, f.getIdFactura());
                ps.setInt(2, f.getIdCliente());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            System.out.println("Datos de Factura cargados con exito!");
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


    private Factura mapear(ResultSet resultado) throws SQLException {
        Factura factura = new Factura();

        factura.setIdFactura(resultado.getInt("idFactura"));
        factura.setIdCliente(resultado.getInt("idCliente"));

        return factura;
    }
}

