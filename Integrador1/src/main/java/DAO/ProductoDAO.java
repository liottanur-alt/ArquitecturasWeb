package DAO;

import java.util.ArrayList;
import DTO.ProductoDTO;
import entities.Producto;


public interface ProductoDAO {

    public ProductoDTO getRecaudacion();

    public void insertarDatos(ArrayList<Producto> productos);

    public void insertarDatosCsv();

    public ArrayList<Producto> getProductos();

}
