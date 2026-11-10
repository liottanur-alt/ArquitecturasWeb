package org.msvcompraentradasol.services;

import java.math.BigDecimal;
import java.util.List;
import org.msvcompraentradasol.dtos.CompraRequestDTO;
import org.msvcompraentradasol.dtos.ReservaRequestDTO;
import org.msvcompraentradasol.dtos.ReservaResponseDTO;
import org.msvcompraentradasol.entities.Compra;
import org.msvcompraentradasol.entities.Entrada;
import org.msvcompraentradasol.enums.EstadoCompra;
import org.msvcompraentradasol.enums.EstadoEntrada;
import org.msvcompraentradasol.feigns.ReservaClientFeign;
import org.msvcompraentradasol.repositories.CompraRepository;
import org.msvcompraentradasol.repositories.EntradaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompraService {

    @Autowired
    private CompraRepository compraRepository;

    @Autowired
    private EntradaRepository entradaRepository;

    @Autowired
    private EntradaService entradaService;

    @Autowired
    private ReservaClientFeign reservaClient;

    @Autowired
    private PagoSimuladoService pagoSimuladoService;

    @Transactional
    public int cancelarEvento(Long idEvento) {
        List<Compra> compras = compraRepository.findAllByIdEventoAndEstadoCompraIn(
                idEvento,
                List.of(EstadoCompra.APROBADA, EstadoCompra.PENDIENTE)
        );
        int cantidadCancelada = 0;

        for (Compra compra : compras) {
            if (compra.getEstadoCompra() == EstadoCompra.PENDIENTE) {
                ReservaResponseDTO liberacion = reservaClient.liberar(compra.getIdReserva());
                if (!"LIBERADA".equals(liberacion.estado())
                        && !"VENCIDA".equals(liberacion.estado())) {
                    throw new IllegalStateException("No se pudo liberar la reserva de la compra " + compra.getId());
                }
                compra.setEstadoCompra(EstadoCompra.CANCELADA);
                compra.setDescripcion("Compra cancelada antes del pago por cancelación del evento");
                compraRepository.save(compra);
                cantidadCancelada++;
                continue;
            }

            List<Entrada> entradas = entradaRepository.findAllByCompra_Id(compra.getId());
            List<Entrada> entradasReintegrables = entradas.stream()
                    .filter(entrada -> entrada.getEstadoEntrada() != EstadoEntrada.DEVOLUCION_ACEPTADA)
                    .filter(entrada -> entrada.getEstadoEntrada() != EstadoEntrada.EVENTO_CANCELADO)
                    .toList();

            BigDecimal importeReintegro = entradasReintegrables.stream()
                    .map(Entrada::getPrecio)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (importeReintegro.signum() > 0) {
                PagoSimuladoService.ResultadoPago reintegro = pagoSimuladoService.reintegrar(
                        compra.getReferenciaPago(),
                        importeReintegro,
                        "cancelacion-evento-" + idEvento + "-compra-" + compra.getId()
                );
                if (!reintegro.aprobado()) {
                    throw new IllegalStateException("No se pudo reintegrar la compra " + compra.getId());
                }
                compra.setReferenciaReintegro(reintegro.referencia());
            }

            entradasReintegrables.forEach(entrada -> entrada.setEstadoEntrada(EstadoEntrada.EVENTO_CANCELADO));
            entradaRepository.saveAll(entradasReintegrables);
            compra.setEstadoCompra(EstadoCompra.CANCELADA);
            compra.setDescripcion("Compra cancelada por cancelación del evento");
            compraRepository.save(compra);
            cantidadCancelada++;
        }

        return cantidadCancelada;
    }

    public Compra crearCompra(CompraRequestDTO dto) {
        List<Long> idsAsientos = dto.getIdsAsientos() == null
                ? List.of()
                : dto.getIdsAsientos();
        int cantidad = idsAsientos.isEmpty()
                ? dto.getCantidadEntradas() == null ? 0 : dto.getCantidadEntradas()
                : idsAsientos.size();

        if (cantidad <= 0) {
            throw new IllegalArgumentException("Debe indicar una cantidad positiva o asientos seleccionados");
        }

        var compraExistente = compraRepository.findByClaveIdempotencia(dto.getClaveIdempotencia());
        if (compraExistente.isPresent()) {
            Compra existente = compraExistente.get();
            if (!coincideConPedido(existente, dto, idsAsientos, cantidad)) {
                throw new IllegalStateException("La clave de idempotencia ya corresponde a otra compra");
            }
            return existente;
        }

        ReservaResponseDTO reserva = reservaClient.reservar(new ReservaRequestDTO(
                dto.getClaveIdempotencia(),
                dto.getIdCliente(),
                dto.getIdEvento(),
                dto.getIdSector(),
                cantidad,
                idsAsientos
        ));

        if (reserva.id() == null || reserva.precioUnitario() == null
                || !"ACTIVA".equals(reserva.estado())) {
            throw new IllegalStateException("SectorAsiento no devolvió una reserva activa con precio");
        }

        Compra compra = new Compra();

        compra.setIdCliente(dto.getIdCliente());
        compra.setIdEvento(dto.getIdEvento());
        compra.setMedioDePago(dto.getMedioDePago());
        compra.setIdSector(dto.getIdSector());
        compra.setIdsAsientos(idsAsientos);
        compra.setCantidadEntradas((long) cantidad);
        compra.setClaveIdempotencia(dto.getClaveIdempotencia());
        compra.setIdReserva(reserva.id());
        compra.setPrecioUnitario(reserva.precioUnitario());
        compra.setImporteTotal(reserva.precioUnitario().multiply(BigDecimal.valueOf(cantidad)));

        compra.setDescripcion("Compra pendiente de pago");
        compra.setEstadoCompra(EstadoCompra.PENDIENTE);

        return compraRepository.save(compra);
    }

    public Compra buscarPorId(Long idCompra) {
        return compraRepository.findById(idCompra)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra no encontrada"));
    }

    private boolean coincideConPedido(
            Compra compra,
            CompraRequestDTO dto,
            List<Long> idsAsientos,
            int cantidad
    ) {
        return compra.getIdCliente().equals(dto.getIdCliente())
                && compra.getIdEvento().equals(dto.getIdEvento())
                && compra.getIdSector().equals(dto.getIdSector())
                && compra.getCantidadEntradas() == cantidad
                && compra.getMedioDePago().equals(dto.getMedioDePago())
                && compra.getIdsAsientos().equals(idsAsientos);
    }

    @Transactional
    public Compra procesarPago(Long idCompra, boolean aprobado) {
        Compra compra = compraRepository.buscarPorIdParaActualizar(idCompra)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Compra no encontrada"));
        if (compra.getEstadoCompra() != EstadoCompra.PENDIENTE) {
            if (aprobado && (compra.getEstadoCompra() == EstadoCompra.APROBADA
                    || compra.getEstadoCompra() == EstadoCompra.EXPIRADA)) {
                return compra;
            }
            if (!aprobado && compra.getEstadoCompra() == EstadoCompra.RECHAZADA) {
                return compra;
            }
            throw new IllegalStateException("La compra ya fue procesada con otro resultado");
        }
        PagoSimuladoService.ResultadoPago resultadoPago = pagoSimuladoService.procesar(
                compra.getImporteTotal(),
                compra.getClaveIdempotencia(),
                aprobado
        );
        compra.setReferenciaPago(resultadoPago.referencia());
        compra.setDescripcion(resultadoPago.detalle());

        if (!resultadoPago.aprobado()) {
            ReservaResponseDTO liberacion = reservaClient.liberar(compra.getIdReserva());
            if (!"LIBERADA".equals(liberacion.estado())
                    && !"VENCIDA".equals(liberacion.estado())) {
                throw new IllegalStateException("La reserva no pudo liberarse tras el rechazo del pago");
            }
            compra.setEstadoCompra(EstadoCompra.RECHAZADA);
            return compraRepository.save(compra);
        }

        ReservaResponseDTO confirmacion = reservaClient.confirmar(compra.getIdReserva());
        if ("VENCIDA".equals(confirmacion.estado())) {
            PagoSimuladoService.ResultadoPago reintegro = pagoSimuladoService.reintegrar(
                    compra.getReferenciaPago(),
                    compra.getImporteTotal(),
                    "reserva-vencida-" + compra.getClaveIdempotencia()
            );
            if (!reintegro.aprobado()) {
                throw new IllegalStateException("No se pudo reintegrar el pago de la reserva vencida");
            }
            compra.setReferenciaReintegro(reintegro.referencia());
            compra.setEstadoCompra(EstadoCompra.EXPIRADA);
            compra.setDescripcion("La reserva venció y el pago simulado fue reintegrado");
            return compraRepository.save(compra);
        }
        if (!"CONFIRMADA".equals(confirmacion.estado())) {
            throw new IllegalStateException("La reserva no pudo confirmarse tras aprobar el pago");
        }

        compra.setEstadoCompra(EstadoCompra.APROBADA);
        List<Long> idsAsientos = compra.getIdsAsientos();

        if (idsAsientos != null && !idsAsientos.isEmpty()) {
            for (Long idAsiento : idsAsientos) {
                entradaService.crearEntrada(
                        compra.getIdCliente(),
                        compra.getIdEvento(),
                        compra.getIdSector(),
                        idAsiento,
                        compra.getPrecioUnitario(),
                        compra);
            }

        } else {
            for (int i = 0; i < compra.getCantidadEntradas(); i++) {
                entradaService.crearEntrada(
                        compra.getIdCliente(),
                        compra.getIdEvento(),
                        compra.getIdSector(),
                        null,
                        compra.getPrecioUnitario(),
                        compra                );
            }
        }
        return compraRepository.save(compra);
    }
}
