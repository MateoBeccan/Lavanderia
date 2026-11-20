package com.miraquetelavo.lavanderia.insumo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import static com.miraquetelavo.lavanderia.insumo.entity.Insumo.LONGITUD_NOMBRE;

public record InsumoRequest(
        @NotBlank @Size(max = LONGITUD_NOMBRE) String nombre,
        @NotNull @Positive Long categoriaId
) {
    public InsumoRequest {
        if (nombre != null) {
            nombre = nombre.strip();
        }
    }
}
