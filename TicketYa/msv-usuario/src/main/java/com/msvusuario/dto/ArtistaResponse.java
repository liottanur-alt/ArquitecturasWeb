package com.msvusuario.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

// Eventos devuelve id; Usuarios conserva idArtista en su contrato HTTP.
public record ArtistaResponse(@JsonAlias("id") Long idArtista, String nombre, String genero) {
}
