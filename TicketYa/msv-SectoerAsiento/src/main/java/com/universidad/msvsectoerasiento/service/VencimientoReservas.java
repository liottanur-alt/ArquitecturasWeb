package com.universidad.msvsectoerasiento.service;
import com.universidad.msvsectoerasiento.repository.ReservaRepository;
import com.universidad.msvsectoerasiento.enums.EstadoReserva;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;

@Component @RequiredArgsConstructor @Slf4j
public class VencimientoReservas {
    private final ReservaRepository reservas;
    private final InventarioService inventario;
    private final Clock reloj;
    /** Busca eventos con reservas vencidas; una falla no impide procesar los demas. */
    @Scheduled(fixedDelayString = "${inventario.barrido-ms:30000}")
    public void ejecutar() {
        for (var evento : reservas.eventosVencidos(EstadoReserva.ACTIVA, reloj.instant())) {
            try { inventario.vencerEvento(evento); }
            catch (RuntimeException e) { log.error("No se pudieron vencer reservas del evento {}", evento, e); }
        }
    }
}
