package DAO;

import java.util.ArrayList;
import DTO.ProductoDTO;
import Entities.Producto;


public interface ProductoDAO {

    public ProductoDTO getRecaudacion();

    public void insertarDatos(ArrayList<Producto> productos);

    public void insertarDatosCsv();

    public ArrayList<Producto> getProductos();

}
