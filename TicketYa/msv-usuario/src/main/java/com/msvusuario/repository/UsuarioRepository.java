package com.msvusuario.repository;

import com.msvusuario.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    boolean existsByRolId(Long idRol);
}
