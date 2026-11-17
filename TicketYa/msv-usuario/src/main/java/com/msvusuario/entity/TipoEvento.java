package com.msvusuario.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

// Valores del contrato de Eventos; no se comparten entidades entre microservicios.
public enum TipoEvento {
    RECITAL, TEATRO, DEPORTE, CONFERENCIA, FESTIVAL, OTRO;

    @JsonCreator
    public static TipoEvento desdeTexto(String valor) {
        // Acepta nombres reconocidos, nunca posiciones numericas del enum.
        return valueOf(valor);
    }
}
