package com.universidad.msvsectoerasiento.dto;
import jakarta.validation.constraints.*;
public record InventarioRequest(@NotNull @Positive Integer limitePorCliente) {}
