package com.msvusuario.repository;

import com.msvusuario.entity.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.ExistsQuery;

// Spring implementa el CRUD sobre MongoDB; Long es el tipo del _id de Usuario.
public interface UsuarioRepository extends MongoRepository<Usuario, Long> {
    java.util.List<Usuario> findByArtistasFavoritosIn(java.util.Collection<Long> artistas);
    // Spring genera estas consultas a partir del nombre del metodo, sin SQL.
    java.util.Optional<Usuario> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByDni(String dni);
    // Busca el ID de la DBRef; ?0 es idRol. Devuelve solo si existe un usuario con ese rol.
    @ExistsQuery("{'rol.$id': ?0}")
    boolean existsByRolId(Long idRol);
}
