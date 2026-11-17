package com.universidad.msvsectoerasiento.service;

import com.universidad.msvsectoerasiento.Entities.*;
import com.universidad.msvsectoerasiento.dto.*;
import com.universidad.msvsectoerasiento.enums.TipoSector;
import com.universidad.msvsectoerasiento.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;

/**
 * Gestiona la estructura fisica; no administra reservas ni ventas por evento.
 * Las consultas son de solo lectura. Las escrituras participan en una transaccion de escritura
 * y se revierten ante errores. @Validated aplica las restricciones incluso cuando
 * otro bean invoca este servicio sin pasar por un controlador.
 */
@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AsientoService {
    private final AsientoRepository asientoRepository;
    private final SectorRepository sectorRepository;
    private final SectorEventoRepository sectorEventoRepository;
    private final AsientoEventoRepository asientoEventoRepository;

    /** Lista asientos fisicos, opcionalmente filtrados por sector; no informa disponibilidad por evento. */
    public List<AsientoResponse> listar(@Positive Long idSector) {
        var asientos = idSector == null ? asientoRepository.findAll() : asientoRepository.findBySectorId(idSector);
        return asientos.stream().map(AsientoResponse::from).toList();
    }

    /** Devuelve ID, sector, fila y columna como DTO. Si el asiento no existe, responde 404. */
    public AsientoResponse obtener(@NotNull @Positive Long id) {
        return AsientoResponse.from(buscar(id));
    }

    /**
     * Crea un asiento en un sector NUMERADO existente y aun no configurado para eventos.
     * Bloquea el sector antes de contar asientos para serializar altas concurrentes.
     * Rechaza capacidad agotada o posicion duplicada con 409; sector inexistente con 404.
     * Normaliza fila y columna y fuerza la escritura: la restriccion unica de base
     * tambien protege contra posiciones duplicadas. No asigna estado ni evento al asiento fisico.
     */
    @Transactional
    public AsientoResponse crear(@NotNull @Valid AsientoRequest request) {
        Sector sector = bloquearSector(request.getIdSector());
        validarSectorEditable(sector);
        if (asientoRepository.countBySectorId(sector.getId()) >= sector.getCapacidadTotal()) {
            throw conflicto("El sector alcanzo su capacidad maxima");
        }
        if (asientoRepository.existsBySectorIdAndFilaAndColumna(sector.getId(),
                request.getFila().trim(), request.getColumna().trim())) {
            throw conflicto("Ya existe un asiento en esa fila y columna");
        }
        Asiento asiento = new Asiento();
        asiento.setSector(sector);
        aplicar(asiento, request);
        return AsientoResponse.from(asientoRepository.saveAndFlush(asiento));
    }

    /**
     * Modifica fila y columna dentro del mismo sector, conservando ID y pertenencia.
     * Bloquea el sector antes de cargar el asiento editable; valida sector y referencias.
     * Comprueba duplicados excluyendo el propio ID, permitiendo conservar su posicion.
     * Responde 404 si falta el asiento/sector y 409 ante traslado, duplicado o asociacion a eventos.
     */
    @Transactional
    public AsientoResponse actualizar(@NotNull @Positive Long id, @NotNull @Valid AsientoRequest request) {
        Long idSector = buscarIdSector(id);
        if (!idSector.equals(request.getIdSector())) {
            throw conflicto("No se permite trasladar un asiento a otro sector");
        }
        Sector sector = bloquearSector(idSector);
        Asiento asiento = buscarEditable(id);
        validarSectorEditable(sector);
        validarSinEvento(id);
        if (asientoRepository.existsBySectorIdAndFilaAndColumnaAndIdNot(sector.getId(),
                request.getFila().trim(), request.getColumna().trim(), id)) {
            throw conflicto("Ya existe otro asiento en esa fila y columna");
        }
        aplicar(asiento, request);
        return AsientoResponse.from(asientoRepository.saveAndFlush(asiento));
    }

    /**
     * Elimina un asiento fisico solo si su distribucion no esta configurada para eventos
     * y no tiene AsientoEvento asociado. Bloquea el sector y comprueba la baja con flush.
     * No borra configuraciones, reservas ni ventas en cascada. Responde 404 o 409 segun el caso.
     */
    @Transactional
    public void eliminar(@NotNull @Positive Long id) {
        Long idSector = buscarIdSector(id);
        Sector sector = bloquearSector(idSector);
        Asiento asiento = buscarEditable(id);
        validarSectorEditable(sector);
        validarSinEvento(id);
        asientoRepository.delete(asiento);
        asientoRepository.flush();
    }

    /**
     * Lee solo la referencia del sector, sin cargar el asiento en el contexto JPA.
     * Permite esperar el bloqueo antes de cargar el estado editable y evita una lectura
     * previa potencialmente desactualizada. Si el asiento no existe, responde 404.
     */
    private Long buscarIdSector(Long id) {
        return asientoRepository.buscarIdSector(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Asiento no encontrado"));
    }

    /** Recupera la entidad dentro de la transaccion actual o lanza 404 si no existe. */
    private Asiento buscar(Long id) {
        return asientoRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Asiento no encontrado"));
    }

    /**
     * Hace una lectura con bloqueo del asiento despues de bloquear su sector.
     * Obtiene el estado actual tambien en bases con aislamiento REPEATABLE READ,
     * evitando depender de la instantanea de la consulta inicial del ID de sector.
     * Responde 404 si otra operacion elimino el asiento mientras se esperaba.
     */
    private Asiento buscarEditable(Long id) {
        return asientoRepository.buscarParaActualizar(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Asiento no encontrado"));
    }

    /** Adquiere el bloqueo de escritura del sector hasta terminar la transaccion; ausencia implica 404. */
    private Sector bloquearSector(Long id) {
        return sectorRepository.buscarParaActualizar(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Sector no encontrado"));
    }

    /** Rechaza con 409 sectores CAMPO o con configuraciones de eventos para mantener estable su distribucion. */
    private void validarSectorEditable(Sector sector) {
        if (sector.getTipo() != TipoSector.NUMERADO) {
            throw conflicto("Solo los sectores numerados admiten asientos");
        }
        if (sectorEventoRepository.existsBySectorId(sector.getId())) {
            throw conflicto("No se puede alterar la distribucion de un sector asociado a eventos");
        }
    }

    /** Protege el asiento contra cambios/bajas si alguna configuracion de evento lo referencia; responde 409. */
    private void validarSinEvento(Long id) {
        if (asientoEventoRepository.existsByAsientoId(id)) {
            throw conflicto("El asiento esta asociado a un evento");
        }
    }

    /** Copia y recorta fila/columna del DTO validado; mantiene ID y sector originales. */
    private void aplicar(Asiento asiento, AsientoRequest request) {
        asiento.setFila(request.getFila().trim());
        asiento.setColumna(request.getColumna().trim());
    }

    /** Construye el error 409 para incumplimientos de reglas de negocio; la transaccion se revierte al lanzarlo. */
    private ResponseStatusException conflicto(String mensaje) {
        return new ResponseStatusException(HttpStatus.CONFLICT, mensaje);
    }
}
