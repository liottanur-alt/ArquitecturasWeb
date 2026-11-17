package com.universidad.msvsectoerasiento.service;

import com.universidad.msvsectoerasiento.Entities.Sector;
import com.universidad.msvsectoerasiento.dto.*;
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
public class SectorService {
    private final SectorRepository sectorRepository;
    private final AsientoRepository asientoRepository;
    private final SectorEventoRepository sectorEventoRepository;

    /** Lista todos los sectores, o solo los del lugar indicado. Un filtro sin resultados devuelve lista vacia. */
    public List<SectorResponse> listar(@Positive Long idLugar) {
        var sectores = idLugar == null ? sectorRepository.findAll() : sectorRepository.findByIdLugar(idLugar);
        return sectores.stream().map(SectorResponse::from).toList();
    }

    /** Busca por ID y devuelve un DTO sin exponer la entidad JPA. Si no existe, responde 404. */
    public SectorResponse obtener(@NotNull @Positive Long id) {
        return SectorResponse.from(buscar(id));
    }

    /**
     * Crea un sector con lugar, nombre sin espacios externos, tipo y capacidad positiva.
     * @Valid valida el DTO antes de ejecutar la operacion. El ID lo genera la base.
     * idLugar es una referencia externa: no se consulta Feign porque el proveedor aun esta pendiente.
     */
    @Transactional
    public SectorResponse crear(@NotNull @Valid SectorRequest request) {
        Sector sector = new Sector();
        aplicar(sector, request);
        return SectorResponse.from(sectorRepository.save(sector));
    }

    /**
     * Reemplaza los datos de un sector existente bajo bloqueo de escritura.
     * Responde 404 si no existe y 409 si tiene asientos o configuraciones de eventos.
     * Esta politica inicial evita cambiar la estructura utilizada por otras entidades.
     */
    @Transactional
    public SectorResponse actualizar(@NotNull @Positive Long id, @NotNull @Valid SectorRequest request) {
        Sector sector = bloquearSector(id);
        verificarSinReferencias(id);
        aplicar(sector, request);
        return SectorResponse.from(sectorRepository.save(sector));
    }

    /**
     * Elimina un sector sin asientos ni eventos asociados. No realiza borrados en cascada.
     * Bloquea el sector, valida sus referencias y ejecuta flush para comprobar restricciones.
     * Responde 404 si falta el sector y 409 si esta en uso.
     */
    @Transactional
    public void eliminar(@NotNull @Positive Long id) {
        Sector sector = bloquearSector(id);
        verificarSinReferencias(id);
        sectorRepository.delete(sector);
        sectorRepository.flush();
    }

    /** Centraliza la consulta por ID y convierte la ausencia en un error HTTP 404. */
    private Sector buscar(Long id) {
        return sectorRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Sector no encontrado"));
    }

    /**
     * Obtiene el sector con bloqueo de escritura hasta el fin de la transaccion.
     * Coordina cambios de capacidad y bajas con las altas de asientos.
     * Si no existe, responde 404. Se usa solo desde metodos transaccionales de escritura.
     */
    private Sector bloquearSector(Long id) {
        return sectorRepository.buscarParaActualizar(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Sector no encontrado"));
    }

    /** Rechaza con 409 cambios o bajas que afectarian asientos o configuraciones historicas de eventos. */
    private void verificarSinReferencias(Long id) {
        if (asientoRepository.existsBySectorId(id) || sectorEventoRepository.existsBySectorId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede modificar ni eliminar un sector con asientos o eventos asociados");
        }
    }

    /** Copia los campos editables del DTO validado y normaliza el nombre; conserva el ID generado. */
    private void aplicar(Sector sector, SectorRequest request) {
        sector.setIdLugar(request.getIdLugar());
        sector.setNombre(request.getNombre().trim());
        sector.setTipo(request.getTipo());
        sector.setCapacidadTotal(request.getCapacidadTotal());
    }
}
