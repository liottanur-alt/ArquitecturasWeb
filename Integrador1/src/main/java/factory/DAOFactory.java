package factory;

import java.sql.Connection;
import DAO.*;
import Repository.mysql.MySQLDAOFactory;
import Repository.postgreSQL.PostgreSQLDAOFactory;

public abstract class DAOFactory {

    private static volatile DAOFactory instance;

    //selecciona la bd con la que va a trabajar
    public static DAOFactory getInstance(DBType type){
        if (instance == null){
            synchronized (DAOFactory.class){
                if (instance == null){
                    switch (type) {
                        case MYSQL:
                            instance = new MySQLDAOFactory();
                            break;

                        case POSTGRES:
                            instance = new PostgreSQLDAOFactory();
                            break;

                        default:
                            throw new IllegalArgumentException("DBType no soportado: " + type);
                    }
                }
            }
        }
        return instance;
    }

    // si no selecciona ninguna usa mysql por default
    public static DAOFactory getInstance() {
        String v = System.getProperty("db.type", "MYSQL");
        DBType type = DBType.valueOf(v.toUpperCase());
        return getInstance(type);
    }

    // se trae los dao segun la bd que tiene
    public abstract ClienteDAO createClienteDAO();
    public abstract FacturaDAO createFacturaDAO();
    public abstract FacturaProductoDAO createFacturaProductoDAO();
    public abstract ProductoDAO createProductoDAO();

    // le entrega una conexion activa
    protected abstract Connection getConnection();

    //cierra la conexxion y resetea la instancia
    public final void shutdown(){
        doShutdown();
        synchronized (DAOFactory.class){
            instance = null;
        }
    }

    protected abstract void doShutdown();
    
}