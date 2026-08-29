package DAO;
import entity.Producto;
import DTO.ProductoDTO;
import java.util.ArrayList;

public interface ProductoDAO {

    public ProductoDTO getRecaudacion();

    public void insertarDatos(ArrayList<Producto> productos);

    public void insertarDatosCsv();

    public ArrayList<Producto> getProductos();

}
