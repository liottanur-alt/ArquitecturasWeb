package com.msvnotificaciones.service;

import com.msvnotificaciones.entity.SucesoNegocio;
import com.msvnotificaciones.dto.SucesoRequest;
import com.msvnotificaciones.repository.SucesoNegocioRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Service
@Validated
@Transactional(readOnly = true)
public class SucesoService {
    private final SucesoNegocioRepository sucesos;
    private final ApplicationEventPublisher eventos;

    public SucesoService(SucesoNegocioRepository sucesos, ApplicationEventPublisher eventos) {
        this.sucesos = sucesos;
        this.eventos = eventos;
    }

    @Transactional
    public SucesoNegocio registrar(@NotNull @Valid SucesoRequest request) {
        var existente = sucesos.findByClaveIdempotencia(request.claveIdempotencia());
        if (existente.isPresent()) return existente.get();
        SucesoNegocio suceso = sucesos.save(new SucesoNegocio(
            request.claveIdempotencia().trim(), request.tipo(), request.idEvento(), request.idCompra(),
            request.idCliente(), limpiar(request.destinatario()), request.cantidadEntradas(),
            request.importe(), request.capacidadTotal(),
            request.fechaSuceso() == null ? Instant.now() : request.fechaSuceso()));
        eventos.publishEvent(new NotificacionAsyncService.SucesoRegistrado(suceso));
        return suceso;
    }

    private String limpiar(String destinatario) {
        return destinatario == null || destinatario.isBlank() ? null : destinatario.trim();
    }
}
