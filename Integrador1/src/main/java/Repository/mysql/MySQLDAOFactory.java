package Repository.mysql;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;

public class MySQLDAOFactory {

        private static final String URL = "jdbc:mysql://localhost:3306/";
        private static final String USER = "root";
        private static final String PASSWORD = "";
        private static final String DB_NAME = "db_arqui";

        public static void main(String[] args) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                System.out.println("Conectado a MySQL.");

                crearBaseDeDatos();
                crearTablas();
                leerUsuariosDesdeCSV("src/main/resources/usuarios.csv");
                leerPedidosDesdeCSV("src/main/resources/pedidos.csv");
                consultarUsuariosYPedidos();

                System.out.println("✅ Operaciones completadas con éxito.");
            } catch (ClassNotFoundException e) {
                System.out.println("Error: No se encontró el driver JDBC de MySQL.");
                e.printStackTrace();
            }
        }

        // Método para abrir conexión a MySQL
        private static Connection getConnection() throws SQLException {
            return DriverManager.getConnection(URL + DB_NAME, USER, PASSWORD);
        }
        /**
         * Método crear la base de datos. Si ya la tengo creada no es necesario
         */
        private static void crearBaseDeDatos() {
            try (Connection conexion = DriverManager.getConnection(URL , USER, PASSWORD);
                 Statement stmt = conexion.createStatement()) {

                String sql = "CREATE DATABASE IF NOT EXISTS " + DB_NAME;
                stmt.executeUpdate(sql);
                System.out.println("Base de datos '" + DB_NAME + "' creada o ya existente.");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }


        /**
         * Método crear las tablas en la base de datos. Si ya las tengo creadas no es necesario
         */
        private static void crearTablas() {
            try (Connection conexion = getConnection();
                 Statement stmt = conexion.createStatement()) {

                String sqlUsuarios = "CREATE TABLE IF NOT EXISTS usuarios ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "nombre VARCHAR(100) NOT NULL, "
                        + "email VARCHAR(100) UNIQUE NOT NULL"
                        + ")";
                stmt.executeUpdate(sqlUsuarios);
                System.out.println("Tabla 'usuarios' creada.");

                String sqlPedidos = "CREATE TABLE IF NOT EXISTS pedidos ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "usuario_id INT, "
                        + "descripcion VARCHAR(255) NOT NULL, "
                        + "fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                        + "FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE"
                        + ")";
                stmt.executeUpdate(sqlPedidos);
                System.out.println("Tabla 'pedidos' creada.");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        /**
         * Método para leer usuarios desde un archivo CSV e insertarlos en la base de datos.
         */

        private static void leerUsuariosDesdeCSV(String archivoCSV) {
            String sql = "INSERT INTO usuarios (id, nombre, email) VALUES (?, ?, ?)";

            try (Connection conexion = getConnection();
                 BufferedReader br = new BufferedReader(new FileReader(archivoCSV));
                 PreparedStatement pstmt = conexion.prepareStatement(sql)) {

                String linea;
                br.readLine(); // Saltar encabezados

                while ((linea = br.readLine()) != null) {
                    String[] datos = linea.split(",");
                    int id = Integer.parseInt(datos[0]);
                    String nombre = datos[1];
                    String email = datos[2];

                    pstmt.setInt(1, id);
                    pstmt.setString(2, nombre);
                    pstmt.setString(3, email);
                    pstmt.executeUpdate();
                }
                System.out.println("Usuarios importados desde CSV.");
            } catch (IOException | SQLException e) {
                e.printStackTrace();
            }
        }


        /**
         * Método para leer pedidos desde un archivo CSV e insertarlos en la base de datos.
         */
        private static void leerPedidosDesdeCSV(String archivoCSV) {
            String sql = "INSERT INTO pedidos (id, usuario_id, descripcion, fecha) VALUES (?, ?, ?, ?)";

            try (Connection conexion = getConnection();
                 BufferedReader br = new BufferedReader(new FileReader(archivoCSV));
                 PreparedStatement pstmt = conexion.prepareStatement(sql)) {

                String linea;
                br.readLine(); // Saltar encabezados

                while ((linea = br.readLine()) != null) {
                    String[] datos = linea.split(",");
                    int id = Integer.parseInt(datos[0]);
                    int usuarioId = Integer.parseInt(datos[1]);
                    String descripcion = datos[2];
                    String fecha = datos[3];

                    pstmt.setInt(1, id);
                    pstmt.setInt(2, usuarioId);
                    pstmt.setString(3, descripcion);
                    pstmt.setString(4, fecha);
                    pstmt.executeUpdate();
                }
                System.out.println("Pedidos importados desde CSV.");
            } catch (IOException | SQLException e) {
                e.printStackTrace();
            }
        }
        /**
         * Método para consultar y mostrar los usuarios con sus pedidos asociados.
         */
        private static void consultarUsuariosYPedidos() {
            String sql = "SELECT u.id AS user_id, u.nombre, u.email, p.id AS pedido_id, p.descripcion, p.fecha " +
                    "FROM usuarios u " +
                    "LEFT JOIN pedidos p ON u.id = p.usuario_id " +
                    "ORDER BY u.id, p.fecha";

            try (Connection conexion = getConnection();
                 Statement stmt = conexion.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                System.out.println("\n=== Lista de Usuarios y sus Pedidos ===");
                int usuarioAnterior = -1;
                while (rs.next()) {
                    int usuarioId = rs.getInt("user_id");
                    String nombre = rs.getString("nombre");
                    String email = rs.getString("email");
                    int pedidoId = rs.getInt("pedido_id");
                    String descripcion = rs.getString("descripcion");
                    Timestamp fecha = rs.getTimestamp("fecha");

                    if (usuarioId != usuarioAnterior) {
                        System.out.println("\nUsuario ID: " + usuarioId + " | Nombre: " + nombre + " | Email: " + email);
                        System.out.println("Pedidos:");
                        usuarioAnterior = usuarioId;
                    }

                    if (pedidoId > 0) {
                        System.out.println("  - Pedido ID: " + pedidoId + " | Descripción: " + descripcion + " | Fecha: " + fecha);
                    } else {
                        System.out.println("  - No tiene pedidos.");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
