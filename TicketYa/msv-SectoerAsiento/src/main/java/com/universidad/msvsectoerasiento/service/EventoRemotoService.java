package com.universidad.msvsectoerasiento.service;

import com.universidad.msvsectoerasiento.client.EventoClientFeign;
import com.universidad.msvsectoerasiento.client.dto.EventoResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service @RequiredArgsConstructor
public class EventoRemotoService {
    private final EventoClientFeign eventos;

    /** Consulta Eventos sin asumir que una falla de red significa que el evento no existe. */
    public EventoResponse obtener(Long id) {
        try {
            var e = eventos.obtenerEvento(id);
            if (e == null || !id.equals(e.id()) || e.idLugar() == null || e.fechaHora() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Eventos devolvio datos incompletos");
            }
            return e;
        } catch (FeignException e) {
            if (e.status() == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Evento no encontrado");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Eventos no esta disponible");
        }
    }

    /** Mantiene el estado AGOTADO sincronizado al vender o devolver cupos. */
    public void actualizarDisponibilidad(Long id, boolean agotado) {
        try {
            eventos.actualizarDisponibilidad(id, agotado);
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo actualizar el estado del evento");
        }
    }
}
