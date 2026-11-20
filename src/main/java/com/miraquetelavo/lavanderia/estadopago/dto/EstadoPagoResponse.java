package com.miraquetelavo.lavanderia.estadopago.dto;

public record EstadoPagoResponse(
        Long id, String codigo, String nombre, String descripcion, Integer orden, Boolean activo) {
}
