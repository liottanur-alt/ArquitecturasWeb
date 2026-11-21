package com.msvusuario.service;

import com.msvusuario.client.EventoClientFeign;
import com.msvusuario.client.CompraClientFeign;
import com.msvusuario.client.InventarioClientFeign;
import com.msvusuario.dto.DisponibilidadLoteRequest;
import com.msvusuario.dto.DisponibilidadEventoResponse;
import com.msvusuario.dto.CandidatosRecomendacionRequest;
import com.msvusuario.dto.EventosPorIdsRequest;
import com.msvusuario.dto.PreferenciasResponse;
import com.msvusuario.dto.HistorialValidoResponse;
import com.msvusuario.dto.ResultadoFuenteRecomendacion;
import com.msvusuario.dto.EventoCatalogoResponse;
import com.msvusuario.dto.FuenteRecomendacion;
import com.msvusuario.dto.RecomendacionCompraResponse;
import com.msvusuario.dto.RecomendacionResponse;
import com.msvusuario.dto.RecomendacionUnificadaResponse;
import com.msvusuario.dto.RecomendacionesResponse;
import com.msvusuario.repository.UsuarioRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class RecomendacionService {
    /** El historial es independiente de las fuentes; se traslada el SecurityContext para el token de servicio. */
    private static final Executor HISTORIAL_EXECUTOR = new DelegatingSecurityContextExecutor(
            Executors.newFixedThreadPool(16, tarea -> {
                Thread hilo = new Thread(tarea, "recomendaciones-historial");
                hilo.setDaemon(true);
                return hilo;
            }));

    private final UsuarioRepository usuarios;
    private final RecomendacionPreferenciasService preferencias;
    private final RecomendacionComprasService compras;
    private final Clock reloj;
    private final EventoClientFeign eventos;
    private final CompraClientFeign clienteCompras;
    private final InventarioClientFeign inventario;

    public ResultadoFuenteRecomendacion<RecomendacionResponse> recomendarPorPreferencias(Long idUsuario) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        CatalogoSolicitud catalogo = new CatalogoSolicitud(ahora);
        List<RecomendacionResponse> resultados = preferencias.recomendar(idUsuario, catalogo::porPreferencias, ahora);
        Historial historial = consultarHistorial(idUsuario);
        InventarioFiltrado<RecomendacionResponse> disponibles = filtrarInventario(
                sinComprados(resultados, RecomendacionResponse::idEvento, historial), RecomendacionResponse::idEvento);
        return new ResultadoFuenteRecomendacion<>(disponibles.resultados(), historial.verificado(), disponibles.verificado());
    }

    public ResultadoFuenteRecomendacion<RecomendacionCompraResponse> recomendarPorCompras(Long idUsuario) {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        CatalogoSolicitud catalogo = new CatalogoSolicitud(ahora);
        List<RecomendacionCompraResponse> resultados = compras.recomendar(idUsuario, catalogo::porIds, ahora);
        Historial historial = consultarHistorial(idUsuario);
        InventarioFiltrado<RecomendacionCompraResponse> disponibles = filtrarInventario(
                sinComprados(resultados, RecomendacionCompraResponse::idEvento, historial), RecomendacionCompraResponse::idEvento);
        return new ResultadoFuenteRecomendacion<>(disponibles.resultados(), historial.verificado(), disponibles.verificado());
    }

    public RecomendacionesResponse recomendar(Long idUsuario, int limite) {
        if (limite < 1 || limite > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limite debe estar entre 1 y 100");
        }
        if (!usuarios.existsById(idUsuario)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }
        CompletableFuture<Historial> historialFuturo = CompletableFuture.supplyAsync(
                () -> consultarHistorial(idUsuario), HISTORIAL_EXECUTOR);
        List<FuenteRecomendacion> noDisponibles = new ArrayList<>();
        LocalDateTime instanteConsulta = LocalDateTime.now(reloj);
        CatalogoSolicitud catalogo = new CatalogoSolicitud(instanteConsulta);
        List<RecomendacionResponse> porPreferencias = consultar(FuenteRecomendacion.PREFERENCIAS,
                () -> preferencias.recomendar(idUsuario, catalogo::porPreferencias, instanteConsulta), noDisponibles);
        List<RecomendacionCompraResponse> porCompras = consultar(FuenteRecomendacion.COMPRAS_EN_COMUN,
                () -> compras.recomendar(idUsuario, catalogo::porIds, instanteConsulta), noDisponibles);
        if (noDisponibles.size() == 2) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudieron obtener recomendaciones de preferencias ni de compras en comun");
        }
        Historial historial = esperar(historialFuturo);
        LocalDateTime ahora = LocalDateTime.now(reloj);
        Map<Long, Acumulada> unicos = new HashMap<>();
        // Preferencias tiene prioridad para los datos descriptivos si ambas fuentes coinciden.
        for (RecomendacionResponse r : porPreferencias) {
            if (!r.fechaHora().isAfter(ahora)) continue;
            Acumulada a = unicos.computeIfAbsent(r.idEvento(), id ->
                    new Acumulada(id, r.nombre(), r.tipo(), r.fechaHora(), r.ciudad()));
            a.preferencias = Math.max(a.preferencias, r.puntuacion());
            a.motivos.addAll(r.motivos());
            a.fuentes.add(FuenteRecomendacion.PREFERENCIAS);
        }
        for (RecomendacionCompraResponse r : porCompras) {
            if (!r.fechaHora().isAfter(ahora)) continue;
            Acumulada a = unicos.computeIfAbsent(r.idEvento(), id ->
                    new Acumulada(id, r.nombre(), r.tipo(), r.fechaHora(), r.ciudad()));
            // Nunca sumar conteos repetidos: pueden referirse a los mismos compradores.
            a.compradores = Math.max(a.compradores, r.compradoresEnComun());
            a.motivos.add(r.motivo());
            a.fuentes.add(FuenteRecomendacion.COMPRAS_EN_COMUN);
        }
        InventarioFiltrado<Acumulada> disponibles = filtrarInventario(sinComprados(new ArrayList<>(unicos.values()),
                recomendacion -> recomendacion.id, historial), recomendacion -> recomendacion.id);
        List<RecomendacionUnificadaResponse> resultado = disponibles.resultados().stream().map(Acumulada::respuesta)
                .sorted(Comparator.comparingInt(RecomendacionUnificadaResponse::puntuacion).reversed()
                        .thenComparing(RecomendacionUnificadaResponse::fechaHora)
                        .thenComparing(RecomendacionUnificadaResponse::idEvento))
                // Limitar despues de combinar, deduplicar y ordenar las dos fuentes completas.
                .limit(limite).toList();
        return new RecomendacionesResponse(resultado, !noDisponibles.isEmpty() || !historial.verificado() || !disponibles.verificado(),
                noDisponibles, historial.verificado(), disponibles.verificado());
    }

    private <T> InventarioFiltrado<T> filtrarInventario(List<T> candidatos, Function<T, Long> idEvento) {
        if (candidatos.isEmpty()) return new InventarioFiltrado<>(candidatos, true);
        List<Long> ids = candidatos.stream().map(idEvento).distinct().sorted().toList();
        try {
            List<DisponibilidadEventoResponse> respuesta = inventario.consultar(new DisponibilidadLoteRequest(ids));
            Map<Long, Boolean> porEvento = new HashMap<>();
            if (respuesta == null) return new InventarioFiltrado<>(candidatos, false);
            for (DisponibilidadEventoResponse fila : respuesta) {
                if (fila == null || fila.idEvento() == null || fila.disponible() == null ||
                        porEvento.putIfAbsent(fila.idEvento(), fila.disponible()) != null) {
                    return new InventarioFiltrado<>(candidatos, false);
                }
            }
            // Una respuesta incompleta o con IDs ajenos no verifica el lote solicitado.
            if (!porEvento.keySet().equals(Set.copyOf(ids))) return new InventarioFiltrado<>(candidatos, false);
            return new InventarioFiltrado<>(candidatos.stream().filter(r -> porEvento.get(idEvento.apply(r))).toList(), true);
        } catch (FeignException exception) {
            return new InventarioFiltrado<>(candidatos, false);
        }
    }

    private record InventarioFiltrado<T>(List<T> resultados, boolean verificado) {}

    private static <T> T esperar(CompletableFuture<T> futuro) {
        try {
            return futuro.join();
        } catch (CompletionException exception) {
            Throwable causa = exception.getCause();
            if (causa instanceof RuntimeException runtime) throw runtime;
            if (causa instanceof Error error) throw error;
            throw exception;
        }
    }

    private Historial consultarHistorial(Long idUsuario) {
        try {
            HistorialValidoResponse respuesta = clienteCompras.historialValido(idUsuario);
            if (respuesta == null || respuesta.idsEventos() == null || respuesta.idsEventos().stream()
                    .anyMatch(id -> id == null || id <= 0)) {
                return new Historial(Set.of(), false);
            }
            return new Historial(Set.copyOf(respuesta.idsEventos()), true);
        } catch (FeignException exception) {
            // No afirmar que el historial esta vacio cuando Compras no pudo verificarlo.
            return new Historial(Set.of(), false);
        }
    }

    private <T> List<T> sinComprados(List<T> resultados, Function<T, Long> idEvento, Historial historial) {
        return resultados.stream().filter(r -> !historial.idsEventos().contains(idEvento.apply(r))).toList();
    }

    private record Historial(Set<Long> idsEventos, boolean verificado) {}

    private <T> List<T> consultar(FuenteRecomendacion fuente, Supplier<List<T>> consulta,
                                 List<FuenteRecomendacion> noDisponibles) {
        try {
            List<T> resultado = consulta.get();
            if (resultado == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La fuente no devolvio un resultado valido");
            return resultado; // Una lista vacia es una consulta exitosa, no una caida.
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != 503) throw exception;
            noDisponibles.add(fuente);
            return List.of();
        }
    }

    // Datos compartidos solo durante esta solicitud. Nunca descarga el catalogo completo.
    private final class CatalogoSolicitud {
        private final LocalDateTime ahora;
        private final Map<Long, EventoCatalogoResponse> conocidos = new HashMap<>();

        private CatalogoSolicitud(LocalDateTime ahora) { this.ahora = ahora; }

        private List<EventoCatalogoResponse> porPreferencias(PreferenciasResponse preferencias) {
            if (preferencias.tiposEventoPreferidos().isEmpty() && preferencias.artistasFavoritos().isEmpty()
                    && (preferencias.ciudadPreferida() == null || preferencias.ciudadPreferida().isBlank())) return List.of();
            try {
                List<EventoCatalogoResponse> respuesta = validar(eventos.buscarCandidatos(new CandidatosRecomendacionRequest(
                        preferencias.tiposEventoPreferidos().stream().map(Enum::name).sorted().toList(),
                        preferencias.artistasFavoritos().stream().sorted().toList(), preferencias.ciudadPreferida(), ahora)));
                recordar(respuesta);
                return respuesta;
            } catch (FeignException exception) { throw noDisponible(); }
        }

        private List<EventoCatalogoResponse> porIds(Set<Long> ids) {
            List<Long> faltantes = ids.stream().filter(id -> !conocidos.containsKey(id)).sorted().toList();
            if (!faltantes.isEmpty()) {
                try {
                    List<EventoCatalogoResponse> respuesta = validar(eventos.consultarPorIds(new EventosPorIdsRequest(faltantes)));
                    Set<Long> solicitados = Set.copyOf(faltantes);
                    if (respuesta.stream().anyMatch(e -> e != null && e.id() != null && !solicitados.contains(e.id()))) throw noDisponible();
                    recordar(respuesta);
                } catch (FeignException exception) { throw noDisponible(); }
            }
            return ids.stream().filter(conocidos::containsKey).map(conocidos::get).toList();
        }

        private void recordar(List<EventoCatalogoResponse> respuesta) {
            respuesta.stream().filter(e -> e != null && e.id() != null).forEach(e -> conocidos.putIfAbsent(e.id(), e));
        }

        private List<EventoCatalogoResponse> validar(List<EventoCatalogoResponse> respuesta) {
            if (respuesta == null) throw noDisponible();
            return Collections.unmodifiableList(new ArrayList<>(respuesta));
        }

        private ResponseStatusException noDisponible() {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudieron consultar los candidatos de Eventos");
        }
    }

    private static final class Acumulada {
        private final Long id;
        private final String nombre, tipo, ciudad;
        private final LocalDateTime fecha;
        private int preferencias;
        private long compradores;
        private final Set<String> motivos = new LinkedHashSet<>();
        private final Set<FuenteRecomendacion> fuentes = EnumSet.noneOf(FuenteRecomendacion.class);

        private Acumulada(Long id, String nombre, String tipo, LocalDateTime fecha, String ciudad) {
            this.id = id; this.nombre = nombre; this.tipo = tipo; this.fecha = fecha; this.ciudad = ciudad;
        }

        private RecomendacionUnificadaResponse respuesta() {
            // Dos puntos por comprador, hasta 10; limitar antes de multiplicar evita overflow.
            int total = preferencias + (int) Math.min(compradores, 5L) * 2;
            return new RecomendacionUnificadaResponse(id, nombre, tipo, fecha, ciudad, total,
                    preferencias, compradores, List.copyOf(motivos), List.copyOf(fuentes));
        }
    }
}
