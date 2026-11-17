package com.msvusuario;

import com.msvusuario.client.InventarioClientFeign;
import com.msvusuario.dto.DisponibilidadEventoResponse;
import com.msvusuario.dto.DisponibilidadLoteRequest;
import static org.mockito.Mockito.*;

public final class InventarioPrueba {
    private InventarioPrueba() {}

    public static void disponibles(InventarioClientFeign cliente) {
        when(cliente.consultar(any(DisponibilidadLoteRequest.class))).thenAnswer(invocacion -> {
            DisponibilidadLoteRequest request = invocacion.getArgument(0);
            return request.idsEventos().stream().map(id -> new DisponibilidadEventoResponse(id, true)).toList();
        });
    }
}
