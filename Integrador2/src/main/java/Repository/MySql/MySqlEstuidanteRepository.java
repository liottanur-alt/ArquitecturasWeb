package main.java.Repository.MySql;


import main.java.Repository.RepoInterfaz;

import java.util.List;

public class MySqlEstuidanteRepository extends RepoInterfaz {
    @Override
    public void guardar(Object objeto) {

    }

    @Override
    public Object buscarPorId(int id) {
        return null;
    }

    @Override
    public List buscarTodos() {
        return List.of();
    }

    @Override
    public void eliminar(Object objeto) {

    }
}
