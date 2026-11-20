package com.miraquetelavo.lavanderia.categoriainsumo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.miraquetelavo.lavanderia.categoriainsumo.entity.CategoriaInsumo.LONGITUD_NOMBRE;

public record CategoriaInsumoRequest(
        @NotBlank @Size(max = LONGITUD_NOMBRE) String nombre
) {
    public CategoriaInsumoRequest {
        if (nombre != null) {
            nombre = nombre.strip();
        }
    }
}
