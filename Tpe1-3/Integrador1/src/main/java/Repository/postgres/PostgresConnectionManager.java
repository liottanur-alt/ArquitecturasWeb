package Repository.postgres;

import factory.ConnectionManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class PostgresConnectionManager implements ConnectionManager {

    private static final String DRIVER = "org.postgresql.Driver";
    private static final String URL = "jdbc:postgresql://localhost:5433/integrador1";
    private static final String USER = "postgres";
    private static final String PASS = "password";

    private static PostgresConnectionManager instance;
    private Connection connection;

    private PostgresConnectionManager() {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static synchronized PostgresConnectionManager getInstance() {
        if (instance == null) {
            instance = new PostgresConnectionManager();
        }
        return instance;
    }

    @Override
    public Connection getConnection() {
        try {
            if (this.connection == null || this.connection.isClosed()) {

                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));

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