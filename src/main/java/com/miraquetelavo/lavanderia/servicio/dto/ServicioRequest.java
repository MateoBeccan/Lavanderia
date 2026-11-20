package com.miraquetelavo.lavanderia.servicio.dto;

import com.miraquetelavo.lavanderia.servicio.entity.ModalidadCobro;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

import static com.miraquetelavo.lavanderia.servicio.entity.Servicio.LONGITUD_NOMBRE;

public record ServicioRequest(
        @NotBlank @Size(max = LONGITUD_NOMBRE) String nombre,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal precio,
        @NotNull ModalidadCobro modalidadCobro
) {
    public ServicioRequest {
        if (nombre != null) {
            nombre = nombre.strip();
        }
    }
}
