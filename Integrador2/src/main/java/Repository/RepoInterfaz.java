package main.java.Repository;


import java.util.List;

public interface RepoInterfaz<T, ID> {

    void guardar(T objeto);

    T buscarPorId(ID id);

    List<T> buscarTodos();

    void eliminar(T objeto);
}
