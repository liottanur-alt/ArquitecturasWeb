package Repository.postgres;

import factory.DAOFactory;
import DAO.*;
import java.sql.Connection;

public class PostgresDAOFactory extends DAOFactory {

    public PostgresDAOFactory() {
        PostgresConnectionManager.getInstance();
    }

    @Override
    protected Connection getConnection() {
        return PostgresConnectionManager.getInstance().getConnection();
    }

    @Override
    protected void doShutdown() {
        PostgresConnectionManager.getInstance().shutdown();
    }

    @Override
    public ClienteDAO createClienteDAO() {
        return PostgresClienteDAO.getInstance(this.getConnection());
    }

    @Override
    public FacturaDAO createFacturaDAO() {
        return PostgresFacturaDAO.getInstance(this.getConnection());
    }

    @Override
    public ProductoDAO createProductoDAO() {
        return PostgresProductoDAO.getInstance(this.getConnection());
    }

    @Override
    public FacturaProductoDAO createFacturaProductoDAO() {
        return PostgresFacturaProductoDAO.getInstance(this.getConnection());
    }
}