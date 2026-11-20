package com.miraquetelavo.lavanderia.metodopago.dto;

public record MetodoPagoResponse(
        Long id, String codigo, String nombre, String descripcion, Boolean activo) {
}
