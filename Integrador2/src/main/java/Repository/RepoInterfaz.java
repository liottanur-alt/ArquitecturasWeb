package main.java.Repository;

import java.util.List;

public interface RepoInterfaz<T> {

    void guardar(T objeto);

    T buscarPorId(int id);

    List<T> buscarTodos();

    void eliminar(T objeto);
}
