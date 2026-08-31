package Repository.mysql;

import DAO.FacturaDAO;
import entities.Factura;

import java.sql.*;
import java.util.ArrayList;

    public class MySQLFacturaDAO implements FacturaDAO {

        private final Connection conexion;

        public MySQLFacturaDAO(Connection conexion) {
            this.conexion = conexion;
            crearTablaSiNoExiste();
        }

        private void crearTablaSiNoExiste() {
            String sql = "CREATE TABLE IF NOT EXISTS facturas (" +
                    "idFactura INT PRIMARY KEY AUTO_INCREMENT, " +
                    "idCliente INT NOT NULL" +
                    ")";

            try (Statement sentencia = conexion.createStatement()) {
                sentencia.execute(sql);
            } catch (SQLException e) {
                throw new RuntimeException("Error creando tabla facturas", e);
            }
        }

        @Override
        public Factura buscarPorId(int id) {
            String sql = "SELECT idFactura, idCliente FROM facturas WHERE idFactura = ?";

            try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {

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
            String sql = "SELECT idFactura, idCliente FROM facturas";

            ArrayList<Factura> facturas = new ArrayList<>();

            try (PreparedStatement sentencia = conexion.prepareStatement(sql);
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
            String sql = "INSERT INTO facturas (idCliente) VALUES (?)";

            try (PreparedStatement sentencia =
                         conexion.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

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
            String sql = "UPDATE facturas SET idCliente = ? WHERE idFactura = ?";

            try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {

                sentencia.setInt(1, factura.getIdCliente());
                sentencia.setInt(2, factura.getIdFactura());

                sentencia.executeUpdate();

            } catch (SQLException e) {
                throw new RuntimeException("Error en actualizar", e);
            }
        }

        @Override
        public void eliminar(Factura factura) {
            String sql = "DELETE FROM facturas WHERE idFactura = ?";

            try (PreparedStatement sentencia = conexion.prepareStatement(sql)) {

                sentencia.setInt(1, factura.getIdFactura());

                sentencia.executeUpdate();

            } catch (SQLException e) {
                throw new RuntimeException("Error en eliminar", e);
            }
        }

        @Override
        public void insertarDatosCsv() {
            // Lo implementamos cuando trabajemos con el CSV.
        }

        private Factura mapear(ResultSet resultado) throws SQLException {
            Factura factura = new Factura();

            factura.setIdFactura(resultado.getInt("idFactura"));
            factura.setIdCliente(resultado.getInt("idCliente"));

            return factura;
        }
    }

