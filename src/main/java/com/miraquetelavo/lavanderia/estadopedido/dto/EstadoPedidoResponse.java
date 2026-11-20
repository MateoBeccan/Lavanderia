package com.miraquetelavo.lavanderia.estadopedido.dto;

public record EstadoPedidoResponse(
        Long id, String codigo, String nombre, String descripcion, Integer orden, Boolean activo) {
}
