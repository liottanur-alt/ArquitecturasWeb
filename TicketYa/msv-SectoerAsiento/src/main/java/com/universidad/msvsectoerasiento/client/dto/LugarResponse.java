package com.universidad.msvsectoerasiento.client.dto;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record LugarResponse(Long id, String nombre, String direccion, String ciudad,
                            Double latitud, Double longitud, String tipo) {
}
