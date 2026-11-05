package com.msvusuario.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "artistas_favoritos")
@Getter
@Setter
@NoArgsConstructor
public class ArtistaFavorito {

    @EmbeddedId
    private ArtistaFavoritoID id;

    @MapsId("idUsuario")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    public ArtistaFavorito(Usuario usuario, Long idArtista) {
        this.usuario = usuario;
        this.id = new ArtistaFavoritoID(usuario.getId(), idArtista);
    }
}
