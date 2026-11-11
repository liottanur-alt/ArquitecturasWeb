
package msvevento.repository;

import msvevento.entity.Evento;
import msvevento.entity.EstadoPublicacion;
import msvevento.entity.TipoEvento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    List<Evento> findByEstadoPublicacion(EstadoPublicacion estadoPublicacion);

    List<Evento> findByIdOrganizador(Long idOrganizador);

    Optional<Evento> findByIdAndEstadoPublicacion(Long id, EstadoPublicacion estadoPublicacion);

    @Query(
            value = """
            SELECT DISTINCT e
            FROM Evento e
            LEFT JOIN e.participantes p
            WHERE e.estadoPublicacion = msvevento.entity.EstadoPublicacion.PUBLICADO
            AND (:tipo IS NULL OR e.tipo = :tipo)
            AND (:idOrganizador IS NULL OR e.idOrganizador = :idOrganizador)
            AND (:idParticipante IS NULL OR p.id = :idParticipante)
            AND (:ciudad IS NULL OR LOWER(e.lugar.ciudad) = LOWER(:ciudad))
            AND (:fechaDesde IS NULL OR e.fechaHora >= :fechaDesde)
            AND (:fechaHasta IS NULL OR e.fechaHora <= :fechaHasta)
            """,
            countQuery = """
            SELECT COUNT(DISTINCT e)
            FROM Evento e
            LEFT JOIN e.participantes p
            WHERE e.estadoPublicacion = msvevento.entity.EstadoPublicacion.PUBLICADO
            AND (:tipo IS NULL OR e.tipo = :tipo)
            AND (:idOrganizador IS NULL OR e.idOrganizador = :idOrganizador)
            AND (:idParticipante IS NULL OR p.id = :idParticipante)
            AND (:ciudad IS NULL OR LOWER(e.lugar.ciudad) = LOWER(:ciudad))
            AND (:fechaDesde IS NULL OR e.fechaHora >= :fechaDesde)
            AND (:fechaHasta IS NULL OR e.fechaHora <= :fechaHasta)
            """
    )
    Page<Evento> buscarConFiltros(
            @Param("tipo") TipoEvento tipo,
            @Param("idOrganizador") Long idOrganizador,
            @Param("idParticipante") Long idParticipante,
            @Param("ciudad") String ciudad,
            @Param("fechaDesde") LocalDateTime fechaDesde,
            @Param("fechaHasta") LocalDateTime fechaHasta,
            Pageable pageable
    );
}
