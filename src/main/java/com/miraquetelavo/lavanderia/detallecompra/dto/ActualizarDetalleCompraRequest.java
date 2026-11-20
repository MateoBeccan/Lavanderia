package com.miraquetelavo.lavanderia.detallecompra.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.miraquetelavo.lavanderia.common.exception.SolicitudInvalidaException;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ActualizarDetalleCompraRequest(
        @Positive Long detalleId,
        @NotNull @Positive Long insumoId,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal cantidad,
        @Positive @Digits(integer = 8, fraction = 2) BigDecimal precioCompra
) {
    @JsonAnySetter
    public void rechazarCampo(String nombre, Object valor) {
        throw new SolicitudInvalidaException("Campo no permitido: " + nombre + ".");
    }
}
