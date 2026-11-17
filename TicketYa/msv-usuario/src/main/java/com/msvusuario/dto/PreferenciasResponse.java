package com.msvusuario.dto;

import com.msvusuario.entity.TipoEvento;
import com.msvusuario.entity.Usuario;
import java.util.Set;

public record PreferenciasResponse(Set<TipoEvento> tiposEventoPreferidos, String ciudadPreferida,
                                  Set<Long> artistasFavoritos) {
    public static PreferenciasResponse from(Usuario usuario) {
        // Copias de los datos locales: consultar no cambia el documento ni necesita Eventos.
        return new PreferenciasResponse(
                usuario.getTiposEventoPreferidos() == null ? Set.of() : Set.copyOf(usuario.getTiposEventoPreferidos()),
                usuario.getCiudadPreferida() == null || usuario.getCiudadPreferida().isBlank()
                        ? null : usuario.getCiudadPreferida(),
                usuario.getArtistasFavoritos() == null ? Set.of() : Set.copyOf(usuario.getArtistasFavoritos()));
    }
}
