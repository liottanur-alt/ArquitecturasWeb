package com.universidad.msvsectoerasiento.repository;
import com.universidad.msvsectoerasiento.Entities.Reserva;
import com.universidad.msvsectoerasiento.enums.EstadoReserva;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {
    Optional<Reserva> findByClaveIdempotencia(String clave);
    List<Reserva> findBySectorEventoIdEvento(Long evento);
    List<Reserva> findBySectorEventoIdEventoAndEstado(Long evento, EstadoReserva estado);
    @Query("select r.sectorEvento.idEvento from Reserva r where r.id = :id")
    Optional<Long> buscarEvento(@Param("id") Long id);
    @Query("select distinct r.sectorEvento.idEvento from Reserva r where r.estado = :estado and r.venceEn <= :ahora")
    List<Long> eventosVencidos(@Param("estado") EstadoReserva estado, @Param("ahora") Instant ahora);
}
