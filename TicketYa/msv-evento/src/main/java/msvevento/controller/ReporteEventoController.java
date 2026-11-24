package msvevento.controller;

import feign.FeignException;
import msvevento.client.CompradoresEventoClient;
import msvevento.client.SectorEventoClient;
import msvevento.service.EventoService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/reportes/eventos")
public class ReporteEventoController {
    private final EventoService eventos;
    private final CompradoresEventoClient compras;
    private final SectorEventoClient sectores;

    public ReporteEventoController(EventoService eventos, CompradoresEventoClient compras, SectorEventoClient sectores) {
        this.eventos = eventos;
        this.compras = compras;
        this.sectores = sectores;
    }

    @GetMapping("/{id}")
    public Reporte consultar(@PathVariable Long id, @AuthenticationPrincipal Jwt usuario) {
        var evento = eventos.buscarEventoPorId(id);
        if (!usuario.getClaimAsStringList("roles").contains("ADMINISTRADOR")
                && !String.valueOf(evento.getIdOrganizador()).equals(usuario.getSubject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo podés consultar reportes de tus eventos.");
        }

        var filas = new TreeMap<Long, Sector>();
        try {
            for (var sector : sectores.listarConfigurados(id)) {
                filas.put(sector.idSector(), new Sector(sector.idSector(), 0, 0, sector.capacidad(), BigDecimal.ZERO, 0.0));
            }
            for (var venta : compras.ventasPorSector(id)) {
                var sector = filas.get(venta.idSector());
                int capacidad = sector == null ? 0 : sector.capacidad();
                filas.put(venta.idSector(), new Sector(venta.idSector(), venta.vendidas(), venta.devueltas(),
                        capacidad, venta.ingresosNetos(), ocupacion(venta.vendidas(), venta.devueltas(), capacidad)));
            }
        } catch (FeignException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No pudimos consultar las ventas o los sectores. Volvé a intentarlo en unos instantes.");
        }
        var detalle = List.copyOf(filas.values());
        long vendidas = detalle.stream().mapToLong(Sector::entradasVendidas).sum();
        long devueltas = detalle.stream().mapToLong(Sector::entradasDevueltas).sum();
        int capacidad = detalle.stream().mapToInt(Sector::capacidad).sum();
        BigDecimal ingresos = detalle.stream().map(Sector::ingresosNetos).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Reporte(id, vendidas, devueltas, capacidad, ingresos, ocupacion(vendidas, devueltas, capacidad), detalle);
    }

    private static Double ocupacion(long vendidas, long devueltas, int capacidad) {
        return capacidad <= 0 ? null : (vendidas - devueltas) * 100.0 / capacidad;
    }

    public record Sector(Long idSector, long entradasVendidas, long entradasDevueltas, int capacidad,
                         BigDecimal ingresosNetos, Double ocupacionPorcentaje) {}

    public record Reporte(Long idEvento, long entradasVendidas, long entradasDevueltas, int capacidadTotal,
                          BigDecimal ingresosNetos, Double ocupacionPorcentaje, List<Sector> sectores) {}
}
