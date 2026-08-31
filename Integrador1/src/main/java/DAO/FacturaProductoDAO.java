package DAO;

import entities.FacturaProducto;
import java.util.ArrayList;

public interface FacturaProductoDAO {

   
    public void insertarDatos(ArrayList<FacturaProducto> facturasProductos);
    
    public void insertarDatosCsv();

    public ArrayList<FacturaProducto> getFacturasProductos();

}