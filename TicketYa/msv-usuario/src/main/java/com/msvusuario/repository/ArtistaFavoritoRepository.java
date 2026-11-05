package com.msvusuario.repository;

import com.msvusuario.entity.ArtistaFavorito;
import com.msvusuario.entity.ArtistaFavoritoID;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ArtistaFavoritoRepository extends JpaRepository<ArtistaFavorito, ArtistaFavoritoID> {
    List<ArtistaFavorito> findByUsuarioId(Long idUsuario);
    void deleteByUsuarioId(Long idUsuario);
}
