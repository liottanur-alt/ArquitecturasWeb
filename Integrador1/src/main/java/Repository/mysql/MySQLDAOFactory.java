package Repository.mysql;

import entities.Cliente;
import factory.DAOFactory;
import factory.ConnectionManager;
import java.sql.Connection;
import DAO.*;

public class MySQLDAOFactory extends DAOFactory {

    /*
    * geminis recomienda poner esto pero no se que es
    *
      private ConnectionManager connectionManager;

        public MySQLDAOFactory() {
            this.connectionManager = MySQLConnectionManager.getInstance();
        }
    *
    * */


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
        return new MySQLClienteDAO(getConnection());
    }

    @Override
    public FacturaDAO createFacturaDAO() {
        return new MySQLFacturaDAO(getConnection());
    }

    @Override
    public ProductoDAO createProductoDAO() {
        return new MySQLProductoDAO(getConnection());
    }
    @Override
    public FacturaProductoDAO createFacturaProductoDAO() {
        return new MySQLFacturaProductoDAO(getConnection());
    }
}
