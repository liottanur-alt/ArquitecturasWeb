package com.msvusuario.service;

import com.msvusuario.dto.EventoCatalogoResponse;
import com.msvusuario.dto.PreferenciasResponse;
import com.msvusuario.dto.RecomendacionResponse;
import com.msvusuario.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class RecomendacionPreferenciasService {
    private final UsuarioRepository usuarios;

    public List<RecomendacionResponse> recomendar(Long idUsuario,
            Function<PreferenciasResponse, List<EventoCatalogoResponse>> consultaCatalogo, LocalDateTime ahora) {
        var usuario = usuarios.findById(idUsuario).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        var preferencias = PreferenciasResponse.from(usuario);
        List<EventoCatalogoResponse> catalogo = consultaCatalogo.apply(preferencias);
        var orden = Comparator.comparingInt(RecomendacionResponse::puntuacion).reversed()
                .thenComparing(RecomendacionResponse::fechaHora)
                .thenComparing(RecomendacionResponse::idEvento)
                // Solo desambigua copias del mismo ID con datos inconsistentes en el catalogo.
                .thenComparing(RecomendacionResponse::nombre)
                .thenComparing(RecomendacionResponse::tipo)
                .thenComparing(RecomendacionResponse::ciudad, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(recomendacion -> String.join("|", recomendacion.motivos()));
        var candidatos = catalogo.stream().filter(evento -> EventosRecomendables.disponible(evento, ahora))
                .map(evento -> puntuar(evento, preferencias))
                .filter(recomendacion -> recomendacion.puntuacion() > 0)
                .sorted(orden).toList();
        // Deduplicar despues de ordenar conserva la mejor coincidencia de cada ID.
        var unicos = new LinkedHashMap<Long, RecomendacionResponse>();
        candidatos.forEach(recomendacion -> unicos.putIfAbsent(recomendacion.idEvento(), recomendacion));
        return List.copyOf(unicos.values());
    }

    private RecomendacionResponse puntuar(EventoCatalogoResponse evento, PreferenciasResponse preferencias) {
        int puntuacion = 0;
        var motivos = new ArrayList<String>();
        // Cada criterio suma una vez, aunque participen varios artistas favoritos.
        if (evento.participantes() != null && evento.participantes().stream().anyMatch(artista ->
                artista != null && artista.id() != null && preferencias.artistasFavoritos().contains(artista.id()))) {
            puntuacion += 5;
            motivos.add("Participa un artista favorito");
        }
        if (preferencias.tiposEventoPreferidos().stream().anyMatch(tipo -> tipo.name().equals(evento.tipo()))) {
            puntuacion += 3;
            motivos.add("Coincide con un tipo de evento preferido");
        }
        String ciudad = evento.lugar() == null ? null : evento.lugar().ciudad();
        String ciudadPreferida = normalizarCiudad(preferencias.ciudadPreferida());
        if (!ciudadPreferida.isEmpty() && ciudadPreferida.equals(normalizarCiudad(ciudad))) {
            puntuacion += 2;
            motivos.add("Se realiza en tu ciudad de preferencia");
        }
        return new RecomendacionResponse(evento.id(), evento.nombre(), evento.tipo(),
                evento.fechaHora(), ciudad, puntuacion, motivos);
    }

    private String normalizarCiudad(String ciudad) {
        if (ciudad == null) return "";
        return Normalizer.normalize(ciudad, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
