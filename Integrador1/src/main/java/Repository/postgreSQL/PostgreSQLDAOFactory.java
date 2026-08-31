package Repository.postgreSQL;


import factory.DAOFactory;
import factory.ConnectionManager;
import java.sql.Connection;
import DAO.*;

public class PostgreSQLDAOFactory extends DAOFactory {
     /*
    * geminis recomienda poner esto pero no se que es
    *
      private ConnectionManager connectionManager;

        public PostgreSQLDAOFactory() {
            this.connectionManager = MySQLConnectionManager.getInstance();
        }
    *
    * */


    @Override
    protected Connection getConnection() {
        return PostgreSQLConnectionManager.getInstance().getConnection();
    }

    @Override
    protected void doShutdown() {
        PostgreSQLConnectionManager.getInstance().shutdown();
    }

    @Override
    public ClienteDAO createClienteDAO() {
        return new PostgreSQLClienteDAO(getConnection());
    }

    @Override
    public FacturaDAO createFacturaDAO() {
        return new PostgreSQLFacturaDAO(getConnection());
    }

    @Override
    public ProductoDAO createProductoDAO() {
        return new PostgreSQLProductoDAO(getConnection());
    }

    @Override
    public FacturaProductoDAO createFacturaProductoDAO() {
        return new PostgreSQLFacturaProductoDAO(getConnection());
    }
}
