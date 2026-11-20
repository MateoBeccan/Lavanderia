package com.miraquetelavo.lavanderia.prenda.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static com.miraquetelavo.lavanderia.prenda.entity.Prenda.LONGITUD_NOMBRE;

public record PrendaRequest(@NotBlank @Size(max = LONGITUD_NOMBRE) String nombre) {

    public PrendaRequest {
        if (nombre != null) {
            nombre = nombre.strip();
        }
    }
}
