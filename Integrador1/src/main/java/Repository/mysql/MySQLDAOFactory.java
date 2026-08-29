package Repository.mysql;

import factory.DAOFactory;
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
        return this.connectionManager.getConnection();
    }

    @Override
    protected void doShutdown() {
        this.connectionManager.shutdown();
    }

    @Override
    public ClienteDAO createClienteDAO(){
        return new MySQLClienteDAO(this.getConnection())
    }

    @Override
    public FacturaDAO createFacturaDAO(){
        return new MySQLFacturaDAO(this.getConnection())
    }

    @Override
    public FacturaProductDAO createFacturaProductoDAO(){
        return new MySQLFacturaProductoDAO(this.getConnection())
    }

    @Override
    public ProductoDAO createProductoDAO(){
        return new MySQLProductoDAO(this.getConnection())
    }

}
