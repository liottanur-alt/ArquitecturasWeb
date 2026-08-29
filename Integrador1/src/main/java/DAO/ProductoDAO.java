package DAO;
import java.sql.Array;
import java.util.ArrayList;
import java.util.List;
import DTO.ProductoDTO;
import Entities.Producto;


public interface ProductoDAO {

    public ProductoDTO getRecaudacion();

    public void insertarDatos(ArrayList<Producto> productos);

    public void insertarDatosCsv();

    public ArrayList<Producto> getProductos();

}
