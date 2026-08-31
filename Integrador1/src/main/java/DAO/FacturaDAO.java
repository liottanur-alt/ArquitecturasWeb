package DAO;

import java.util.ArrayList;
import entities.Factura;

    public interface FacturaDAO {

        Factura buscarPorId(int id);
        ArrayList<Factura> buscarTodas();
        void insertar(Factura f);
        void actualizar(Factura f);
        void eliminar(Factura f);
        void insertarDatosCsv();

    }
