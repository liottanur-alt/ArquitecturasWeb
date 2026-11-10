package com.universidad.msvsectoerasiento.service;

import com.universidad.msvsectoerasiento.Entities.*;
import com.universidad.msvsectoerasiento.dto.*;
import com.universidad.msvsectoerasiento.enums.*;
import com.universidad.msvsectoerasiento.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

/** Cada operacion bloquea la misma cabecera de evento: funciona tambien entre distintas JVM. */
@Service @Validated @RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class InventarioService {
    private final InventarioEventoRepository inventarios;
    private final SectorRepository sectores;
    private final SectorEventoRepository configuraciones;
    private final AsientoRepository asientos;
    private final AsientoEventoRepository posiciones;
    private final ReservaRepository reservas;
    private final DevolucionRepository devoluciones;
    private final EventoRemotoService eventos;
    private final Clock reloj;
    @Value("${inventario.reserva-minutos:10}") private long minutosReserva;
    @Value("${inventario.zona-eventos:America/Argentina/Buenos_Aires}") private String zona;

    /** Inicializa el inventario con datos reales de Eventos. Repetir el mismo limite es seguro. */
    public void configurarEvento(@NotNull @Positive Long id, @NotNull @Valid InventarioRequest request) {
        var remoto = eventos.obtener(id);
        var actual = inventarios.bloquear(id);
        if (actual.isPresent()) {
            var e = actual.get();
            if (e.isCancelado() || !e.getLimitePorCliente().equals(request.limitePorCliente()))
                throw conflicto("El inventario ya esta configurado con otra politica o cancelado");
            return;
        }
        var e = new InventarioEvento();
        e.setId(id); e.setIdLugar(remoto.idLugar()); e.setFechaHora(remoto.fechaHora());
        e.setLimitePorCliente(request.limitePorCliente());
        inventarios.saveAndFlush(e);
    }

    /** Asocia un sector y su precio; copia sus asientos fisicos como disponibilidad del evento. */
    public DisponibilidadResponse configurarSector(@NotNull @Positive Long evento, @NotNull @Valid SectorEventoRequest request) {
        var e = bloquear(evento); validarVenta(e);
        var sector = sectores.buscarParaActualizar(request.idSector()).orElseThrow(() -> falta("Sector"));
        if (!sector.getIdLugar().equals(e.getIdLugar())) throw conflicto("El sector no pertenece al lugar del evento");
        var previa = configuraciones.findByIdEventoAndSectorId(evento, sector.getId());
        if (previa.isPresent()) {
            var c = previa.get();
            if (c.getPrecio().compareTo(request.precio()) != 0 || !c.getCapacidadHabilitada().equals(request.capacidadHabilitada()))
                throw conflicto("El sector ya tiene otra configuracion para este evento");
            return disponibilidad(c, e);
        }
        if (request.capacidadHabilitada() > sector.getCapacidadTotal()) throw conflicto("La capacidad supera el limite fisico");
        var fisicos = asientos.findBySectorId(sector.getId());
        if (sector.getTipo() == TipoSector.NUMERADO && fisicos.size() != request.capacidadHabilitada())
            throw conflicto("La capacidad numerada debe coincidir con los asientos creados");
        var c = new SectorEvento(); c.setIdEvento(evento); c.setSector(sector);
        c.setPrecio(request.precio()); c.setCapacidadHabilitada(request.capacidadHabilitada());
        configuraciones.saveAndFlush(c);
        if (sector.getTipo() == TipoSector.NUMERADO) {
            for (var fisico : fisicos) {
                var p = new AsientoEvento(); p.setAsiento(fisico); p.setSectorEvento(c); posiciones.save(p);
            }
            posiciones.flush();
        }
        return disponibilidad(c, e);
    }

    /** Libera vencimientos antes de informar cupos. La respuesta no debe cachearse. */
    public List<DisponibilidadResponse> consultar(@NotNull @Positive Long evento) {
        var e = bloquear(evento); vencer(evento);
        return configuraciones.findByIdEvento(evento).stream().map(c -> disponibilidad(c, e)).toList();
    }

    /** Consulta un sector del evento y devuelve 404 si no fue configurado. */
    public DisponibilidadResponse consultarSector(@NotNull @Positive Long evento, @NotNull @Positive Long sector) {
        var e = bloquear(evento); vencer(evento);
        return disponibilidad(configuracion(evento, sector), e);
    }

    /** Reserva cupo/asientos y registra una clave unica para soportar reintentos del mismo pedido. */
    public ReservaResponse reservar(@NotNull @Valid ReservaRequest request) {
        var e = bloquear(request.idEvento()); vencer(e.getId());
        var anterior = reservas.findByClaveIdempotencia(request.claveIdempotencia());
        if (anterior.isPresent()) {
            var r = anterior.get();
            if (!mismoPedido(r, request)) throw conflicto("La clave de idempotencia ya identifica otro pedido");
            return ReservaResponse.from(r);
        }
        validarVentaRemota(e);
        var c = configuracion(e.getId(), request.idSector());
        var ids = idsUnicos(request.idAsientos());
        boolean numerado = c.getSector().getTipo() == TipoSector.NUMERADO;
        if ((numerado && ids.size() != request.cantidad()) || (!numerado && !ids.isEmpty()))
            throw conflicto("Cantidad y asientos no corresponden al tipo de sector");
        // El limite se aplica entre todos los sectores y cuenta reservas activas y ventas no devueltas.
        long acumuladas = reservas.findBySectorEventoIdEvento(e.getId()).stream()
            .filter(r -> r.getIdCliente().equals(request.idCliente()))
            .filter(r -> r.getEstado() == EstadoReserva.ACTIVA || r.getEstado() == EstadoReserva.CONFIRMADA)
            .mapToLong(r -> r.getCantidad() - r.getCantidadDevuelta()).sum();
        if ((long) acumuladas + request.cantidad() > e.getLimitePorCliente()) throw conflicto("Limite por cliente excedido");
        List<AsientoEvento> elegidos = new ArrayList<>();
        if (numerado) {
            elegidos = posiciones.findBySectorEventoIdOrderByIdAsc(c.getId()).stream()
                .filter(p -> ids.contains(p.getAsiento().getId())).toList();
            if (elegidos.size() != ids.size()) throw conflicto("Hay asientos que no pertenecen a este sector/evento");
            if (elegidos.stream().anyMatch(p -> p.getEstado() != EstadoAsiento.DISPONIBLE)) throw conflicto("Asiento no disponible");

        /** Modofica el estado de los asientos y los reserva temporalmenta */
            elegidos.forEach(p -> p.setEstado(EstadoAsiento.RESERVADO));
        } else {
            if (c.getEspaciosDisponibles() < request.cantidad()) throw conflicto("Cupo insuficiente");
        }
        c.setLugaresReservados(c.getLugaresReservados() + request.cantidad());
        var r = new Reserva(); r.setClaveIdempotencia(request.claveIdempotencia());
        r.setIdCliente(request.idCliente()); r.setSectorEvento(c); r.setCantidad(request.cantidad());
        r.setPrecioUnitario(c.getPrecio()); r.setCreadaEn(reloj.instant());
        r.setVenceEn(reloj.instant().plus(Duration.ofMinutes(minutosReserva)));
        r.setAsientos(new ArrayList<>(elegidos)); reservas.saveAndFlush(r);
        return ReservaResponse.from(r);
    }

    /** Consulta y actualiza el vencimiento antes de devolver el estado de una reserva. */
    public ReservaResponse obtenerReserva(@NotNull @Positive Long id) {
        var e = bloquearEventoReserva(id); vencer(e.getId());
        return ReservaResponse.from(reserva(id));
    }

    /** Confirma una sola vez. Si ya vencio, devuelve VENCIDA y conserva la liberacion en la base. */
    public ReservaResponse confirmar(@NotNull @Positive Long id) {
        var e = bloquearEventoReserva(id); vencer(e.getId()); var r = reserva(id);
        if (r.getEstado() == EstadoReserva.CONFIRMADA) return ReservaResponse.from(r);
        if (r.getEstado() == EstadoReserva.VENCIDA) return ReservaResponse.from(r);
        if (r.getEstado() != EstadoReserva.ACTIVA || e.isCancelado()) throw conflicto("La reserva no se puede confirmar");
        validarVentaRemota(e);
        var c = r.getSectorEvento();
        c.setLugaresReservados(c.getLugaresReservados() - r.getCantidad());
        c.setLugaresVendidos(c.getLugaresVendidos() + r.getCantidad());
        r.getAsientos().forEach(p -> p.setEstado(EstadoAsiento.VENDIDO)); r.setEstado(EstadoReserva.CONFIRMADA);
        return ReservaResponse.from(r);
    }

    /** Compensa un pago rechazado: libera solo reservas activas. Repetir la llamada no resta cupo de nuevo. */
    public ReservaResponse liberar(@NotNull @Positive Long id) {
        var e = bloquearEventoReserva(id); vencer(e.getId()); var r = reserva(id);
        if (r.getEstado() == EstadoReserva.CONFIRMADA || r.getEstado() == EstadoReserva.DEVUELTA)
            throw conflicto("Una venta requiere devolucion, no liberacion de reserva");
        if (r.getEstado() == EstadoReserva.ACTIVA) soltar(r, EstadoReserva.LIBERADA);
        return ReservaResponse.from(r);
    }

    /** Libera una devolucion aprobada, total o parcial, respetando el plazo de 72 horas. No reintegra dinero. */
    public ReservaResponse devolver(@NotNull @Positive Long id, @NotNull @Valid DevolucionRequest request) {
        var e = bloquearEventoReserva(id); var r = reserva(id);
        var ids = idsUnicos(request.idAsientos()); var firma = firma(ids);
        var previa = devoluciones.findByClave(request.claveIdempotencia());
        if (previa.isPresent()) {
            var d = previa.get();
            if (!d.getReserva().getId().equals(id) || !d.getCantidad().equals(request.cantidad()) || !d.getPosiciones().equals(firma) || !d.getSolicitadaEn().equals(request.solicitadaEn().truncatedTo(java.time.temporal.ChronoUnit.MICROS)))
                throw conflicto("La clave de devolucion ya identifica otra solicitud");
            return ReservaResponse.from(r);
        }
        if (r.getEstado() != EstadoReserva.CONFIRMADA || e.isCancelado()) throw conflicto("La reserva no es una venta vigente");
        // La fecha se consulta de nuevo para contemplar reprogramaciones del evento.
        var remoto = eventos.obtener(e.getId());
        var inicio = remoto.fechaHora().atZone(ZoneId.of(zona)).toInstant();
        if (request.solicitadaEn().isBefore(r.getCreadaEn()) || request.solicitadaEn().isAfter(reloj.instant()) || request.solicitadaEn().isAfter(inicio.minus(Duration.ofHours(72)))) throw conflicto("La devolucion requiere al menos 72 horas de anticipacion");
        devolverLugares(r, request.cantidad(), ids);
        var d = new Devolucion(); d.setClave(request.claveIdempotencia()); d.setReserva(r);
        d.setCantidad(request.cantidad()); d.setSolicitadaEn(request.solicitadaEn().truncatedTo(java.time.temporal.ChronoUnit.MICROS)); d.setPosiciones(firma); devoluciones.saveAndFlush(d);
        return ReservaResponse.from(r);
    }

    /** Cancela el inventario y libera toda reserva/venta pendiente. Compras debe gestionar reintegros y avisos. */
    public void cancelar(@NotNull @Positive Long evento) {
        var e = bloquear(evento);
        if (e.isCancelado()) return;
        for (var r : reservas.findBySectorEventoIdEvento(evento)) {
            if (r.getEstado() == EstadoReserva.ACTIVA) soltar(r, EstadoReserva.LIBERADA);
            else if (r.getEstado() == EstadoReserva.CONFIRMADA) {
                var ids = r.getAsientos().stream().filter(a -> !r.getAsientosDevueltos().contains(a))
                    .map(a -> a.getAsiento().getId()).toList();
                devolverLugares(r, r.getCantidad() - r.getCantidadDevuelta(), new HashSet<>(ids));
            }
        }
        e.setCancelado(true);
    }

    /** Punto transaccional invocado por el planificador; cada evento se procesa por separado. */
    public void vencerEvento(@NotNull @Positive Long evento) { bloquear(evento); vencer(evento); }

    /** Bloquea primero la cabecera comun; todas las escrituras siguen el mismo orden. */
    private InventarioEvento bloquear(Long id) { return inventarios.bloquear(id).orElseThrow(() -> falta("Inventario del evento")); }

    /** Localiza la cabecera sin cargar la reserva antes de adquirir el bloqueo. */
    private InventarioEvento bloquearEventoReserva(Long id) {
        return bloquear(reservas.buscarEvento(id).orElseThrow(() -> falta("Reserva")));
    }

    /** Recupera una reserva dentro de la transaccion protegida. */
    private Reserva reserva(Long id) { return reservas.findById(id).orElseThrow(() -> falta("Reserva")); }

    /** Obtiene la configuracion del sector para un evento. */
    private SectorEvento configuracion(Long evento, Long sector) {
        return configuraciones.findByIdEventoAndSectorId(evento, sector).orElseThrow(() -> falta("Sector del evento"));
    }

    /** Verifica publicacion y reprogramaciones contra Eventos antes de reservar o confirmar. */
    private void validarVentaRemota(InventarioEvento e) {
        var remoto = eventos.obtener(e.getId());
        if (!e.getIdLugar().equals(remoto.idLugar())) throw conflicto("El evento cambio de lugar; requiere nueva configuracion");
        if (!"PUBLICADO".equals(remoto.estadoPublicacion()) || Set.of("CANCELADO", "FINALIZADO", "AGOTADO").contains(Objects.toString(remoto.estado(), "")))
            throw conflicto("El evento no esta habilitado para vender");
        e.setFechaHora(remoto.fechaHora()); validarVenta(e);
    }

    /** Rechaza operaciones nuevas sobre eventos cancelados o que ya comenzaron. */
    private void validarVenta(InventarioEvento e) {
        if (e.isCancelado() || !reloj.instant().isBefore(e.getFechaHora().atZone(ZoneId.of(zona)).toInstant()))
            throw conflicto("El evento esta cancelado o ya comenzo");
    }

    /** Expira reservas activas y libera sus lugares en la misma transaccion. */
    private void vencer(Long evento) {
        reservas.findBySectorEventoIdEventoAndEstado(evento, EstadoReserva.ACTIVA).stream()
            .filter(r -> !r.getVenceEn().isAfter(reloj.instant())).forEach(r -> soltar(r, EstadoReserva.VENCIDA));
    }

    /** Devuelve solo el cupo retenido por esta reserva, sin alterar ventas ni otras reservas. */
    private void soltar(Reserva r, EstadoReserva estado) {
        r.getAsientos().forEach(p -> p.setEstado(EstadoAsiento.DISPONIBLE));
        var c = r.getSectorEvento(); c.setLugaresReservados(c.getLugaresReservados() - r.getCantidad()); r.setEstado(estado);
    }

    /** Aplica una devolucion y guarda sus asientos historicos para impedir devolver dos veces el mismo lugar. */
    private void devolverLugares(Reserva r, int cantidad, Set<Long> ids) {
        if (cantidad > r.getCantidad() - r.getCantidadDevuelta()) throw conflicto("Cantidad superior a la venta pendiente");
        if (r.getSectorEvento().getSector().getTipo() == TipoSector.NUMERADO) {
            var elegidos = r.getAsientos().stream().filter(a -> ids.contains(a.getAsiento().getId())).toList();
            if (ids.size() != cantidad || elegidos.size() != cantidad || elegidos.stream().anyMatch(r.getAsientosDevueltos()::contains))
                throw conflicto("Asientos ajenos a la compra o ya devueltos");
            elegidos.forEach(a -> a.setEstado(EstadoAsiento.DISPONIBLE)); r.getAsientosDevueltos().addAll(elegidos);
        } else if (!ids.isEmpty()) throw conflicto("Campo no admite IDs de asientos");
        var c = r.getSectorEvento(); c.setLugaresVendidos(c.getLugaresVendidos() - cantidad);
        r.setCantidadDevuelta(r.getCantidadDevuelta() + cantidad);
        if (r.getCantidadDevuelta().equals(r.getCantidad())) r.setEstado(EstadoReserva.DEVUELTA);
    }

    /** Compara el contenido original: una clave repetida no permite cambiar cliente ni lugares. */
    private boolean mismoPedido(Reserva r, ReservaRequest q) {
        return r.getIdCliente().equals(q.idCliente()) && r.getSectorEvento().getIdEvento().equals(q.idEvento())
            && r.getSectorEvento().getSector().getId().equals(q.idSector()) && r.getCantidad().equals(q.cantidad())
            && r.getAsientos().stream().map(a -> a.getAsiento().getId()).collect(java.util.stream.Collectors.toSet()).equals(idsUnicos(q.idAsientos()));
    }

    /** Rechaza IDs duplicados y devuelve una coleccion para comparar sin depender del orden. */
    private Set<Long> idsUnicos(List<Long> ids) {
        var unicos = new HashSet<>(ids); if (unicos.size() != ids.size()) throw conflicto("Asientos duplicados en la solicitud"); return unicos;
    }

    /** Canoniza los IDs para comparar reintentos de devoluciones. */
    private String firma(Set<Long> ids) { return ids.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")); }

    /** Arma una fotografia consistente de cupos y posiciones del sector. */
    private DisponibilidadResponse disponibilidad(SectorEvento c, InventarioEvento e) {
        var lista = posiciones.findBySectorEventoIdOrderByIdAsc(c.getId()).stream()
            .map(a -> new DisponibilidadResponse.Posicion(a.getAsiento().getId(), a.getAsiento().getFila(), a.getAsiento().getColumna(), a.getEstado())).toList();
        return new DisponibilidadResponse(c.getIdEvento(), c.getSector().getId(), c.getSector().getTipo(), c.getPrecio(),
            c.getCapacidadHabilitada(), e.isCancelado() ? 0 : c.getEspaciosDisponibles(), c.getLugaresReservados(), c.getLugaresVendidos(), e.isCancelado(), lista);
    }

    /** Error 404 para recursos inexistentes. */
    private ResponseStatusException falta(String recurso) { return new ResponseStatusException(HttpStatus.NOT_FOUND, recurso + " no encontrado"); }
    /** Error 409 para solicitudes incompatibles con el estado del inventario. */
    private ResponseStatusException conflicto(String mensaje) { return new ResponseStatusException(HttpStatus.CONFLICT, mensaje); }
}
