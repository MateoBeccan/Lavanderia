package com.miraquetelavo.lavanderia.cobro.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RegistrarCobroRequest(
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal monto,
        @NotNull @Positive Long metodoPagoId
) {
    @JsonAnySetter
    public void rechazarCampoDesconocido(String campo, Object valor) {
        throw new IllegalArgumentException("La solicitud contiene un campo no admitido.");
    }
}
