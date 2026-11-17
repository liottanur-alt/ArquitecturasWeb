package msvevento.service;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Join;
import msvevento.dto.CandidatosRecomendacionRequest;
import msvevento.dto.EventosPorIdsRequest;
import msvevento.dto.EventoRecomendacionResponse;
import msvevento.entity.Evento;
import msvevento.entity.Artista;
import msvevento.entity.EstadoEvento;
import msvevento.entity.EstadoPublicacion;
import msvevento.repository.EventoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ConsultaRecomendacionService {
    private final EventoRepository eventos;
    public ConsultaRecomendacionService(EventoRepository eventos) { this.eventos = eventos; }

    public List<EventoRecomendacionResponse> candidatos(CandidatosRecomendacionRequest request) {
        String ciudad = CiudadNormalizada.de(request.ciudadPreferida());
        if (request.tiposEventoPreferidos().isEmpty() && request.artistasFavoritos().isEmpty() && ciudad.isEmpty()) return List.of();
        Specification<Evento> filtro = (root, query, builder) -> {
            query.distinct(true);
            List<Predicate> coincidencias = new ArrayList<>();
            if (!request.tiposEventoPreferidos().isEmpty()) coincidencias.add(root.get("tipo").in(request.tiposEventoPreferidos()));
            if (!request.artistasFavoritos().isEmpty()) {
                Subquery<Long> participantes = query.subquery(Long.class);
                Root<Evento> candidato = participantes.from(Evento.class);
                Join<Evento, Artista> artista = candidato.join("participantes");
                participantes.select(candidato.get("id")).where(builder.equal(candidato.get("id"), root.get("id")),
                        artista.get("id").in(request.artistasFavoritos()));
                // EXISTS evita multiplicar las filas de la coleccion que carga el EntityGraph.
                coincidencias.add(builder.exists(participantes));
            }
            if (!ciudad.isEmpty()) coincidencias.add(builder.equal(root.join("lugar", JoinType.LEFT).get("ciudadNormalizada"), ciudad));
            return builder.and(builder.equal(root.get("estadoPublicacion"), EstadoPublicacion.PUBLICADO),
                    builder.equal(root.get("estado"), EstadoEvento.EN_CURSO),
                    builder.greaterThan(root.get("fechaHora"), request.fechaDesde()),
                    builder.or(coincidencias.toArray(Predicate[]::new)));
        };
        // Sin pagina ni limite: Usuarios combina las fuentes y calcula la clasificacion completa.
        return eventos.findAll(filtro, Sort.by("id")).stream().map(EventoRecomendacionResponse::de).toList();
    }

    public List<EventoRecomendacionResponse> porIds(EventosPorIdsRequest request) {
        if (request.idsEventos().isEmpty()) return List.of();
        Specification<Evento> filtro = (root, query, builder) -> {
            query.distinct(true);
            return builder.and(root.get("id").in(request.idsEventos()),
                    builder.equal(root.get("estadoPublicacion"), EstadoPublicacion.PUBLICADO));
        };
        // Incluye pasados publicados para conservar los nombres de los eventos de referencia de Compras.
        return eventos.findAll(filtro, Sort.by("id")).stream().map(EventoRecomendacionResponse::de).toList();
    }
}
