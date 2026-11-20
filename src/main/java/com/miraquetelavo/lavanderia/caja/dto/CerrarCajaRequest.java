package com.miraquetelavo.lavanderia.caja.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CerrarCajaRequest(@NotNull @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal efectivoContado) {
}
