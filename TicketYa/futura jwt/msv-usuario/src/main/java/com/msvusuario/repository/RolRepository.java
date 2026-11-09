package com.msvusuario.repository;

import com.msvusuario.entity.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolRepository extends JpaRepository<Rol, Long> {
    java.util.Optional<Rol> findByName(com.msvusuario.entity.TipoRol name);
}
