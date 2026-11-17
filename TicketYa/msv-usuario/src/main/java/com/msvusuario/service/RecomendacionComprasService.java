package com.msvusuario.service;

import com.msvusuario.client.CompraClientFeign;
import com.msvusuario.dto.CompraEnComunResponse;
import com.msvusuario.dto.EventoCatalogoResponse;
import com.msvusuario.dto.RecomendacionCompraResponse;
import com.msvusuario.repository.UsuarioRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class RecomendacionComprasService {
    private final UsuarioRepository usuarios;
    private final CompraClientFeign compras;

    public List<RecomendacionCompraResponse> recomendar(Long idUsuario,
            Function<Set<Long>, List<EventoCatalogoResponse>> consultaCatalogo, LocalDateTime ahora) {
        if (!usuarios.existsById(idUsuario)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }
        List<CompraEnComunResponse> candidatos;
        try {
            candidatos = compras.recomendar(idUsuario);
        } catch (FeignException exception) {
            throw noDisponible("No se pudieron consultar las compras en comun");
        }
        if (candidatos == null || candidatos.stream().anyMatch(c -> c == null || c.idEvento() == null
                || c.idEvento() <= 0 || c.compradoresEnComun() == null || c.compradoresEnComun() <= 0
                || c.idEventoReferencia() == null || c.idEventoReferencia() <= 0
                || c.idEvento().equals(c.idEventoReferencia()))) {
            throw noDisponible("Compras devolvio una respuesta agregada invalida");
        }
        if (candidatos.isEmpty()) return List.of();
        Set<Long> ids = new TreeSet<>();
        candidatos.forEach(c -> { ids.add(c.idEvento()); ids.add(c.idEventoReferencia()); });
        List<EventoCatalogoResponse> catalogo = consultaCatalogo.apply(ids);
        // Tambien contiene nombres de eventos pasados publicados, utiles como referencia X.
        var porId = new HashMap<Long, EventoCatalogoResponse>();
        catalogo.stream().filter(e -> e != null && e.id() != null).forEach(e -> porId.putIfAbsent(e.id(), e));
        Map<Long, CompraEnComunResponse> unicos = new HashMap<>();
        var prioridad = Comparator.comparingLong((CompraEnComunResponse c) -> c.compradoresEnComun()).reversed()
                .thenComparing(CompraEnComunResponse::idEventoReferencia);
        // Nunca suma filas repetidas: podria contar dos veces a los mismos compradores.
        candidatos.forEach(c -> unicos.merge(c.idEvento(), c, (a, b) -> prioridad.compare(a, b) <= 0 ? a : b));
        return unicos.values().stream()
                .filter(c -> EventosRecomendables.disponible(porId.get(c.idEvento()), ahora))
                .map(c -> respuesta(c, porId))
                .sorted(Comparator.comparingLong(RecomendacionCompraResponse::compradoresEnComun).reversed()
                        .thenComparing(RecomendacionCompraResponse::fechaHora)
                        .thenComparing(RecomendacionCompraResponse::idEvento))
                .toList();
    }

    private RecomendacionCompraResponse respuesta(CompraEnComunResponse candidato, Map<Long, EventoCatalogoResponse> catalogo) {
        var evento = catalogo.get(candidato.idEvento());
        var origen = catalogo.get(candidato.idEventoReferencia());
        String referencia = origen != null && origen.nombre() != null && !origen.nombre().isBlank()
                ? origen.nombre() : "el evento #" + candidato.idEventoReferencia();
        return new RecomendacionCompraResponse(evento.id(), evento.nombre(), evento.tipo(), evento.fechaHora(),
                evento.lugar() == null ? null : evento.lugar().ciudad(), candidato.compradoresEnComun(),
                "Clientes que compraron " + referencia + " también compraron " + evento.nombre());
    }

    private ResponseStatusException noDisponible(String mensaje) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, mensaje);
    }
}
