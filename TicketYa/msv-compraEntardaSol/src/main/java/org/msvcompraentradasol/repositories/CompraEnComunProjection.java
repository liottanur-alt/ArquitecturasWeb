package org.msvcompraentradasol.repositories;

// Resultado agregado: nunca contiene clientes, compras ni entradas individuales.
public interface CompraEnComunProjection {
    Long getIdEvento();
    Long getCompradoresEnComun();
    Long getIdEventoReferencia();
}
