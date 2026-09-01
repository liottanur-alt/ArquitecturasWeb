package Repository.mysql;

import factory.DAOFactory;
import Entities.Cliente;
import factory.ConnectionManager;

import java.sql.Connection;

import DAO.*;

public class MySQLDAOFactory extends DAOFactory {

    private ConnectionManager connectionManager;

    public MySQLDAOFactory() {
        this.connectionManager = MySQLConnectionManager.getInstance();
    }


    @Override
    protected Connection getConnection() {
        return MySQLConnectionManager.getInstance().getConnection();
    }

    @Override
    protected void doShutdown() {
        MySQLConnectionManager.getInstance().shutdown();
    }

    @Override
    public ClienteDAO createClienteDAO() {
        return MySQLClienteDAO.getInstance(this.getConnection());
    }

    @Override
    public FacturaDAO createFacturaDAO() {
        return MySQLFacturaDAO.getInstance(this.getConnection());
    }

    @Override
    public ProductoDAO createProductoDAO() {
        return MySQLProductoDAO.getInstance(this.getConnection());
    }

    @Override
    public FacturaProductoDAO createFacturaProductoDAO() {

        return MySQLFacturaProductoDAO.getInstance(this.getConnection());
    }
}
