package Repository.mysql;

import factory.ConnectionManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MySQLConnectionManager implements ConnectionManager {

    private static final String DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String URL = "jdbc:mysql://localhost:3306/integrador1";
    private static final String USER = "root";
    private static final String PASS = "password";

    private static MySQLConnectionManager instance;
    private Connection connection;

    private MySQLConnectionManager() {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static synchronized MySQLConnectionManager getInstance() {
        if (instance == null) {
            instance = new MySQLConnectionManager();
        }
        return instance;
    }

    @Override
    public Connection getConnection() {
        try {
            if (this.connection == null || this.connection.isClosed()) {
                this.connection = DriverManager.getConnection(URL, USER, PASS);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al conectar a la base de datos MySQL", e);
        }
        return this.connection;
    }

    @Override
    public void shutdown() {
        try {
            if (this.connection != null && !this.connection.isClosed()) {
                this.connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            this.connection = null;
            instance = null;
        }
    }
}