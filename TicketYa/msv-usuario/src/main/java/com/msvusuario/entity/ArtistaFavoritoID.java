package com.msvusuario.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ArtistaFavoritoID implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "id_usuario", nullable = false)
    private Long idUsuario;

    // Referencia al artista administrado por otro microservicio, sin FK local.
    @Column(name = "id_artista", nullable = false)
    private Long idArtista;
}
