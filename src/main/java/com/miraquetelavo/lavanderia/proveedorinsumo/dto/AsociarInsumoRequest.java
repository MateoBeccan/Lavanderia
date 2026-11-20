package com.miraquetelavo.lavanderia.proveedorinsumo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AsociarInsumoRequest(@NotNull @Positive Long insumoId) {
}
