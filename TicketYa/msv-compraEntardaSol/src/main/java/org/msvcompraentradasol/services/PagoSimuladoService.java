package org.msvcompraentradasol.services;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PagoSimuladoService {

    public ResultadoPago procesar(
            BigDecimal importe,
            String claveIdempotencia,
            boolean aprobado
    ) {
        String referencia = "SIM-" + UUID.nameUUIDFromBytes(
                claveIdempotencia.getBytes(StandardCharsets.UTF_8)
        );
        String detalle = aprobado
                ? "Pago aprobado por el simulador"
                : "Pago rechazado por el simulador";

        return new ResultadoPago(aprobado, referencia, importe, detalle);
    }

    public ResultadoPago reintegrar(
            String referenciaPago,
            BigDecimal importe,
            String claveIdempotencia
    ) {
        if (referenciaPago == null || importe == null || importe.signum() < 0) {
            return new ResultadoPago(false, null, importe, "No se pudo validar el reintegro");
        }

        String referencia = "SIM-REF-" + UUID.nameUUIDFromBytes(
                claveIdempotencia.getBytes(StandardCharsets.UTF_8)
        );
        return new ResultadoPago(true, referencia, importe, "Reintegro aprobado por el simulador");
    }

    public record ResultadoPago(
            boolean aprobado,
            String referencia,
            BigDecimal importe,
            String detalle
    ) {
    }
}
