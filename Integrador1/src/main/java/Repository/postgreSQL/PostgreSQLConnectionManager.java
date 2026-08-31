package Repository.postgreSQL;

import factory.ConnectionManager;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class PostgreSQLConnectionManager implements ConnectionManager {

    private static final String DRIVER = "org.postgresql.Driver";
    private static final String URL = "jdbc:postgresql://localhost:5432/integrador1";
    private static final String USER = "root";
    private static final String PASS = "password";

    private static PostgreSQLConnectionManager instance;
    private Connection connection;

    private PostgreSQLConnectionManager() {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static synchronized PostgreSQLConnectionManager getInstance() {
        if (instance == null) {
            instance = new PostgreSQLConnectionManager();
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
            throw new RuntimeException("Error al conectar a la base de datos PostgreSQL", e);
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