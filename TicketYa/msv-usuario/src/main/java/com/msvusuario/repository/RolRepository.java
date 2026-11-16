package com.msvusuario.repository;

import com.msvusuario.entity.Rol;
import org.springframework.data.mongodb.repository.MongoRepository;

// Spring implementa el CRUD de documentos Rol con IDs numericos.
public interface RolRepository extends MongoRepository<Rol, Long> {
    // El nombre del metodo genera una consulta por el campo name.
    java.util.Optional<Rol> findByName(com.msvusuario.entity.TipoRol name);
}
